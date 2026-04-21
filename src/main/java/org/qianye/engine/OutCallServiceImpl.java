package org.qianye.engine;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.qianye.DTO.*;
import org.qianye.cache.CacheClient;
import org.qianye.cache.QueueGroupRedisCache;
import org.qianye.cache.RedisLock;
import org.qianye.callback.OutCallBackServiceComposite;
import org.qianye.common.*;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.listener.OutCallEndEvent;
import org.qianye.listener.OutCallStartEvent;
import org.qianye.service.OutboundCallTaskService;
import org.qianye.service.OutcallQueueGroupService;
import org.qianye.service.OutcallQueueService;
import org.qianye.service.impl.RemoteFsApi;
import org.qianye.service.impl.TaskPlanService;
import org.qianye.util.CommonUtil;
import org.qianye.util.LoggerUtil;
import org.qianye.util.RandomUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OutCallServiceImpl implements OutCallService {
    private final Map<String, AtomicBoolean> task2RunningFlag = new ConcurrentHashMap<>(16);
    @Resource
    protected CacheClient cacheClient;
    @Resource
    private OutcallQueueGroupService queueGroupService;
    @Resource
    private OutcallQueueService queueDetailService;
    @Resource
    private RedisLock redisLock;
    @Resource
    private OutCallBackServiceComposite outCallBackServiceComposite;
    @Resource
    private OutboundCallTaskService outboundCallTaskService;
    @Resource
    private QueueGroupRedisCache groupRedisCache;
    @Resource
    private OutCallRateLimitServiceImpl outCallRateLimitService;
    @Resource
    private TaskPlanService taskPlanService;
    @Resource
    private OutCallScheduleDrm outCallScheduleDrm = new OutCallScheduleDrm();
    @Value("${env}")
    private String env;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private QueueGroupRedisCache queueGroupRedisCache;
    @Resource
    private InterceptTodayRecallService interceptTodayRecallService;
    @Resource
    private RemoteFsApi remoteFsApi;
    private int makeCallMaxPoolSize = 160;
    private int commonMakeCallCorePoolSize = 40;

    @Override
    public void outCall() {
        try {
            LoggerUtil.info(log, "outCall start");
            // 分页处理所有进行中的任务
            int pageNum = 1;
            int pageSize = 200;
            PageDTO<OutboundCallTaskDO> processingTaskPage;
            do {
                processingTaskPage = outboundCallTaskService.queryProcessingTaskList(pageNum, pageSize);
                if (CollectionUtils.isEmpty(processingTaskPage.getRecords())) {
                    LoggerUtil.info(log, "outCall no processing task on page {}, ignore it", pageNum);
                    break;
                }
                LoggerUtil.info(log, "outCall processing task on page {},task size:{}", pageNum, processingTaskPage.getRecords().size());
                for (OutboundCallTaskDO task : processingTaskPage.getRecords()) {
                    if (isTaskStatusAndTimeRangeValid(task)) {
                        CompletableFuture.runAsync(new Runnable() {
                            @Override
                            public void run() {
                                executeGroupOutCall(task);
                            }
                        }, OutCallExecutorService.getOutCallThreadPool());
                    }
                }
                // 处理下一页
                pageNum++;
            } while (processingTaskPage.getRecords().size() == pageSize); // 当返回记录数小于pageSize时，说明已经处理完所有数据
        } catch (Exception e) {
            LoggerUtil.error(log, "createDummyLogContext error", e);
        }
    }

    @Override
    public void executeGroupOutCall(OutboundCallTaskDO task) {
        if (Objects.isNull(task)) {
            return;
        }
        LoggerUtil.info(log, "task start,task:{}", task.getTaskCode());
        String instanceId = task.getInstanceId();
        String taskCode = task.getTaskCode();
        AtomicBoolean runningFlag = task2RunningFlag.computeIfAbsent(taskCode, k -> new AtomicBoolean(false));
        if (!runningFlag.compareAndSet(false, true)) {
            LoggerUtil.info(log, "outCall is running, taskCode:{},ignore it", taskCode);
            return;
        }
        if (!isTaskStatusAndTimeRangeValid(task)) {
            LoggerUtil.info(log, "task status or time range invalid,instanceId:{} taskCode:{},status:{}", task.getInstanceId(),
                    task.getTaskCode(), task.getTaskStatus());
            // 释放运行标志
            runningFlag.set(false);
            return;
        }
        LoggerUtil.info(log, "execute queue OutCall start,instanceId:{},taskCode:{}", task.getInstanceId(), task.getTaskCode());
        long taskStartTime = System.currentTimeMillis();
        // 添加循环计数器，避免无限循环
        int groups = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // 控制请求速率
                controlRequestRate();
                updatePoolConfigIfNeeded();
                ThreadPoolExecutor makeCallThreadPool = getThreadPoolExecutor(instanceId);
                if (makeCallThreadPool.getQueue().size() > outCallScheduleDrm.getMakeCallMaxQueueSize()) {
                    // 当前排队数过多，直接返回
                    LoggerUtil.info(log, "makeCallThreadPool queue is too large,instanceId:{},taskCode:{},queueSize:{}",
                            instanceId, taskCode, makeCallThreadPool.getQueue().size());
                    break;
                }
                if (!waitForRateLimitRelease("default", task)) {
                    // 等待下一轮再来询问，释放线程资源
                    LoggerUtil.info(log, "waitForRateLimitRelease,waiting next call task, taskCode:{},instanceId:{}", taskCode,
                            instanceId);
                    long groupSize = queueGroupRedisCache.getGroupSize(taskCode, env, instanceId, false);
                    if (groupSize == 0 && groups > 0) {
                        eventPublisher.publishEvent(new OutCallEndEvent(task));
                    }
                    runningFlag.set(false);
                    break;
                }
                List<String> allGroups = groupRedisCache.popRightGroup(taskCode, env, instanceId, outCallScheduleDrm.getPollFirstGroupSize());
                LoggerUtil.info(log, "getGroupFromCache,instanceId:{},taskCode:{},groupSize:{},groups:{}",
                        instanceId, taskCode, allGroups.size(), allGroups);
                try {
                    if (CollectionUtils.isEmpty(allGroups)) {
                        // 再查询一下是否还有未处理的队列
                        if (CollectionUtils.isEmpty(queryWaitingQueues(task))) {
                            LoggerUtil.info(log, "No more groups to process,update task status to finish,"
                                            + "instanceId:{}, taskCode:{},set to runningFlag:{}",
                                    instanceId, taskCode, runningFlag.get());
                        } else {
                            LoggerUtil.info(log, "No more groups to process,wait next loop,instanceId:{}, taskCode:{}",
                                    instanceId, taskCode);
                        }
                        // 发布结束事件
                        eventPublisher.publishEvent(new OutCallEndEvent(task));
                        runningFlag.set(false);
                        break;
                    }
                    // 首次执行，发布事件
                    if (groups == 0) {
                        eventPublisher.publishEvent(new OutCallStartEvent(task));
                    }
                    // 状态校验
                    List<String> notPlanningGroupCodes = queueGroupService.queryQueueGroupByCodes(instanceId, allGroups)
                            .stream().filter(group -> group.getGroupStatus() != GroupStatus.PLANNING)
                            .map(QueueGroupDTO::getQueueGroupCode)
                            .collect(Collectors.toList());
                    if (!CollectionUtils.isEmpty(notPlanningGroupCodes)) {
                        // 告警
                        LoggerUtil.info(log, "groupStatusError,notPlanning,instanceId:{}," +
                                        " taskCode:{},notPlanningGroups:{}",
                                instanceId, taskCode, notPlanningGroupCodes);
                    }
                    allGroups.removeAll(notPlanningGroupCodes);
                    if (CollectionUtils.isEmpty(allGroups)) {
                        LoggerUtil.info(log, "all groups status not planning,ignore it,instanceId:{}, taskCode:{}", instanceId, taskCode);
                        continue;
                    }
                } catch (Exception e) {
                    LoggerUtil.error(log, e, "preHandleGroup error");
                    // 将所有的队列组的全部改为waiting
                    allGroups.forEach(
                            groupCode -> queueGroupService.updateQueueGroupStatus(instanceId, groupCode, GroupStatus.WAITING, null));
                    runningFlag.set(false);
                    break;
                }
                allGroups.forEach(
                        groupCode -> queueGroupService.updateQueueGroupStatus(instanceId, groupCode, GroupStatus.PROCESSING, null));
                LoggerUtil.info(log, "update groupCode status to processing,instanceId:{},taskCode:{},queueGroupCode:{}",
                        task.getInstanceId(), task.getTaskCode(), allGroups);
                CountDownLatch latch = new CountDownLatch(allGroups.size());
                long currentTimeMillis = System.currentTimeMillis();
                for (String groupCode : allGroups) {
                    OutCallExecutorService.getQueueGroupthreadPool().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                handleGroup(instanceId, taskCode, groupCode);
                                LoggerUtil.info(log, "handleGroupAsync,instanceId:{},taskCode:{},groupCode:{},cost time:{}", instanceId,
                                        taskCode, groupCode, formatTime(System.currentTimeMillis() - currentTimeMillis));
                            } catch (Exception ex) {
                                LoggerUtil.error(log, "handleGroupAsync error", ex);
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                }
                latch.await();
            }
            LoggerUtil.info(log, "executeOneBatchGroupEnd,instanceId:{}, taskCode:{},handleGroups:{}, time:{}");
        } catch (Exception e) {
            runningFlag.set(false);
            LoggerUtil.error(log, "executeOutCall exception, taskCode:{}", taskCode, e);
        } finally {
            // 确保无论如何都能清理状态
            task2RunningFlag.remove(taskCode);
            LoggerUtil.info(log, "taskOutCallEnd,instanceId:{},taskCode:{},time:{},groups:{}",
                    instanceId, taskCode, formatTime(System.currentTimeMillis() - taskStartTime), groups);
        }
    }

    private List<QueueDetailDTO> queryWaitingQueues(OutboundCallTaskDO task) {
        QueueDetailRequest request = new QueueDetailRequest();
        request.setInstanceId(task.getInstanceId());
        request.setTaskCode(task.getTaskCode());
        request.setEnv(task.getEnvFlag());
        request.setPageNum(1);
        request.setPageSize(10);
        request.setStatus(QueueStatus.WAITING);
        DateTime dateTime = new DateTime();
        request.setEndTime(dateTime.toDate());
        request.setStartTime(dateTime.withTimeAtStartOfDay().toDate());
        return queueDetailService.queryPage(request).getList();
    }

    private void handleGroup(String instanceId, String taskCode, String groupCode) {
        QueueGroupDTO queueGroupDTO = null;
        try {
            // 重新加载最新的任务信息
            OutboundCallTaskDO task = outboundCallTaskService.queryOneCallTaskByCode(instanceId, taskCode);
            // 任务状态检查
            if (!TaskStatusEnum.allowCallUpgrade(task.getTaskStatus())) {
                // 如果是暂停，将所有的队列组的全部改为waiting,直到缓存数据消耗完毕
                if (Objects.equals(TaskStatusEnum.STOP.getCode(), task.getTaskStatus())) {
                    Map<String, Object> extInfo = new HashMap<>();
                    extInfo.put(OutCallResult.RETRY_REASON, OutCallResult.STATUS_INVALID);
                    queueGroupService.updateQueueGroupStatus(instanceId, groupCode, GroupStatus.WAITING, extInfo);
                    LoggerUtil.info(log, ",waitingNextCall,update status to waiting, "
                                    + "queueGroupCode:{},taskCode:{},instanceId:{}",
                            groupCode, taskCode, instanceId);
                }
                LoggerUtil.info(log, ",taskStatusInvalid,instanceId:{}, taskCode:{},status:{}", task.getInstanceId(), task.getTaskCode(),
                        task.getTaskStatus());
                return;
            }
            // 呼叫时间检查
            CallTimeRange callTimeRange = taskPlanService.loadTaskCallTimeRange(instanceId, taskCode);
            CallTimeRange.CallInfo callTimeInfo = callTimeRange.calCallTimeInfo();
            queueGroupDTO = queueGroupService.queryQueueGroupByCode(instanceId, groupCode);
            if (queueGroupDTO == null) {
                LoggerUtil.info(log, "outCall groupCode is null, queueGroupCode:{}", groupCode);
                return;
            }
            if (callTimeInfo.isInCurrentCallTimeRange()) {
                LoggerUtil.info(log, "find out group,queueGroupDTO instanceId:{},taskCode:{},groupCode:{}", instanceId, taskCode,
                        groupCode);
                if (GroupStatus.STOP == queueGroupDTO.getGroupStatus()) {
                    LoggerUtil.info(log, "outCall task is stop,instanceId:{},taskCode:{} queueGroupCode:{}",
                            instanceId, taskCode, queueGroupDTO.getQueueGroupCode());
                    return;
                }
                doGroupOutCall(queueGroupDTO, callTimeRange, task);
            } else {
                // 不在当前呼叫时间, 在任务时间范围内
                if (callTimeInfo.isInTaskTimeRange()) {
                    queueGroupDTO.setGroupStatus(GroupStatus.WAITING);
                    queueGroupDTO.getExtInfo().put(OutCallResult.NOT_MATCH_TIME, OutCallResult.NOT_MATCH_TIME);
                    queueGroupService.updateQueueGroupStatus(queueGroupDTO);
                    LoggerUtil.info(log, ",waitingNextCall,update status to waiting, queueGroupCode:{},taskCode:{}，instanceId:{}",
                            queueGroupDTO.getQueueGroupCode(), queueGroupDTO.getTaskCode(), instanceId);
                } else {
                    // 不在任务时间范围内，更新组状态为停止,对应的队列也停止
                    queueGroupDTO.setGroupStatus(GroupStatus.STOP);
                    queueGroupDTO.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.NOT_MATCH_TIME);
                    queueGroupService.stopQueueGroupAndQueue(queueGroupDTO);
                    LoggerUtil.info(log, ",notMatchCallTime:{}, queueGroupCode:{},,taskCode:{},update status to stop",
                            CommonUtil.simplifyTimeRange(queueGroupDTO.getCallTimeRange()), queueGroupDTO.getQueueGroupCode(),
                            queueGroupDTO.getTaskCode());
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(log, "executeQueueGroupCall exception," + "instanceId:{},taskCode:{},queueGroupCode:{}", instanceId,
                    queueGroupDTO.getTaskCode(), groupCode, e);
            Map<String, Object> extinfo = new HashMap<>();
            extinfo.put(OutCallResult.STOP_REASON, e.getMessage());
            queueGroupService.updateQueueGroupStatus(instanceId, groupCode, GroupStatus.STOP, extinfo);
            QueueGroupDTO retryGroup = queueGroupDTO;
            CompletableFuture.runAsync(new TracerRunnable() {
                @Override
                public void doRun() {
                    taskPlanService.replanExceptionTask(retryGroup, retryGroup.getQueueCodes());
                }
            }, OutCallExecutorService.getRetryThreadPool());
        }
    }

    /**
     * @return true: 合法，false: 不合法
     */
    private boolean isTaskStatusAndTimeRangeValid(OutboundCallTaskDO task) {
        if (!TaskStatusEnum.allowCallUpgrade(task.getTaskStatus())) {
            // 任务状态不合法
            LoggerUtil.info(log, ",taskStatusInvalid, taskCode:{},status:{}", task.getTaskCode(), task.getTaskStatus());
            return false;
        }
        boolean inTodayCallTimeRange = taskPlanService.isInCallTimeRange(task.getInstanceId(), task.getTaskCode());
        if (!inTodayCallTimeRange) {
            // 时间范围不合法
            LoggerUtil.info(log, ",taskTimeRangeInvalid, taskCode:{},instanceId:{},ignore it", task.getTaskCode(), task.getInstanceId());
            return false;
        }
        return true;
    }

    /**
     *
     */
    private void doGroupOutCall(QueueGroupDTO queueGroupDTO, CallTimeRange groupCallTimeRange, OutboundCallTaskDO task) {
        List<String> queueCodes = queueGroupDTO.getQueueCodes();
        if (CollectionUtils.isEmpty(queueCodes)) {
            return;
        }
        long currentTimeMillis = System.currentTimeMillis();
        String instanceId = queueGroupDTO.getInstanceId();
        String taskCode = queueGroupDTO.getTaskCode();
        // 限流 时间超时10秒，跳本次队列组，更新状态为失败
        if (!waitForRateLimitRelease(queueGroupDTO.getQueueGroupCode(), task)) {
            // 更新组状态为失败
            queueGroupDTO.setGroupStatus(GroupStatus.WAITING);
            queueGroupDTO.getExtInfo().put(OutCallResult.RETRY_REASON, OutCallResult.FLOW_LIMIT);
            int rows = queueGroupService.updateQueueGroupStatus(queueGroupDTO);
            LoggerUtil.info(log, "waitForRateLimitRelease,groupOutCall rate limit exceeded, instanceId:{}, taskCode:{}, queueGroupCode:{},effect db row:{}",
                    instanceId, taskCode, queueGroupDTO.getQueueGroupCode(), rows);
            return;
        }
        try {
            // 查询队列详情
            List<QueueDetailDTO> queueDetails = queueDetailService.getByCodes(queueGroupDTO.getInstanceId(), queueCodes);
            List<QueueDetailDTO> notPlanningQueues = queueDetails.stream()
                    .filter(data -> data.getStatus() != QueueStatus.PLANNING).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(notPlanningQueues)) {
                // 防止重复呼叫，只处理规划中的队列
                LoggerUtil.info(log, "queueStatusError,notPlanning, ignore it," +
                                "instanceId:{},taskCode:{}, queueGroupCode:{},notPlanningQueues:{}",
                        queueGroupDTO.getInstanceId(), queueGroupDTO.getTaskCode(), queueGroupDTO.getQueueGroupCode(),
                        notPlanningQueues.stream().map(QueueDetailDTO::getQueueCode).collect(Collectors.toList()));
            }
            //移除状态异常的
            queueDetails.removeAll(notPlanningQueues);
            // 业务前置过滤
            List<QueueDetailDTO> inValidatedQueues = outCallBackServiceComposite.findInvalidQueues(queueDetails, task);
            if (!CollectionUtils.isEmpty(inValidatedQueues)) {
                LoggerUtil.info(log, "outCallInValidatedQueues,instanceId:{},taskCode:{}, queueGroupCode:{},invalidQueues:{}",
                        queueGroupDTO.getInstanceId(), queueGroupDTO.getTaskCode(), queueGroupDTO.getQueueGroupCode(),
                        inValidatedQueues.stream().map(QueueDetailDTO::getQueueCode).collect(Collectors.toList()));
                inValidatedQueues.forEach(queueDetail -> queueDetail.setStatus(QueueStatus.STOP));
                queueDetailService.updateQueues(inValidatedQueues);
            }
            // 移除业务前置过滤的队列
            queueDetails.removeAll(inValidatedQueues);
            // 异步处理队列，不等待结果，立即返回
            processQueuesASync(queueGroupDTO, queueDetails, task, groupCallTimeRange);
            LoggerUtil.info(log, "processQueuesASync submitted,instanceId:{},taskCode:{},queueGroupCode:{},queueSize:{},cost:{}",
                    queueGroupDTO.getInstanceId(), queueGroupDTO.getTaskCode(), queueGroupDTO.getQueueGroupCode(), queueDetails.size(),
                    formatTime(System.currentTimeMillis() - currentTimeMillis));
        } catch (Exception e) {
            LoggerUtil.error(log, "outCallException, queueGroupCode:{}", queueGroupDTO.getQueueGroupCode(), e);
            queueGroupDTO.setGroupStatus(GroupStatus.STOP);
            queueGroupDTO.getExtInfo().put(OutCallResult.STOP_REASON, e.getMessage());
            int rows = queueGroupService.updateQueueGroupStatus(queueGroupDTO);
            // todo 监控告警
            LoggerUtil.info(log, "update QueueGroup Status , queueGroupCode:{},effect db row:{},replan task",
                    queueGroupDTO.getQueueGroupCode(), rows);
            CompletableFuture.runAsync(() -> taskPlanService.replanExceptionTask(queueGroupDTO, queueGroupDTO.getQueueCodes()),
                    OutCallExecutorService.getRetryThreadPool());
        }
    }

    private ThreadPoolExecutor getThreadPoolExecutor(String tenantId) {
        ThreadPoolExecutor makeCallThreadPool = OutCallExecutorService.getCommonMakeCallThreadPool();
        if (tenantId != null && outCallScheduleDrm.getLargeOutCallTenantId().contains(tenantId)) {
            makeCallThreadPool = OutCallExecutorService.getLargeMakeCallThreadPool();
        }
        return makeCallThreadPool;
    }

    /**
     * 带超时的限流等待检查
     *
     * @return true-在超时时间内限流解除，false-超时仍未解除限流
     */
    private boolean waitForRateLimitRelease(String groupCode, OutboundCallTaskDO taskDO) {
        try {
            long timeoutMs = outCallScheduleDrm.getRateLimitWaitingTimeSecond() * 1000;
            long startTime = System.currentTimeMillis();
            long remainingTimeout = timeoutMs;
            long sleepMs = outCallScheduleDrm.getFlowLimitSleepMs();
            int i = 1;
            LoggerUtil.info(log, "waitForRateLimitStart,instanceId:{},taskCode:{},queueGroupCode:{},remainingTimeout:{}ms,extInfo:{}",
                    taskDO.getInstanceId(), taskDO.getTaskCode(), groupCode, remainingTimeout, taskDO.getExtInfo());
            while (remainingTimeout > 0) {
                i++;
                if (!outCallRateLimitService.isReachedRateLimit(groupCode, taskDO)) {
                    return true; // 限流已解除
                }
                LoggerUtil.info(log, ",RateLimitReached, waiting...,instanceId:{}, queueGroupCode:{},remainingTimeout:{}ms",
                        taskDO.getInstanceId(), groupCode, remainingTimeout);
                try {
                    Thread.sleep(sleepMs);
                    remainingTimeout = timeoutMs - (System.currentTimeMillis() - startTime);
                    // 休眠重新查询任务
                    if (i > 10) {
                        taskDO = outboundCallTaskService.queryOneCallTaskByCode(taskDO.getInstanceId(), taskDO.getTaskCode());
                        log.info("waitForRateLimitRelease,update task,instanceId:{},taskCode:{},queueGroupCode:{},remainingTimeout:{}ms",
                                taskDO.getInstanceId(), taskDO.getTaskCode(), groupCode, remainingTimeout);
                        i = 1;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Rate limit wait interrupted, queueGroupCode:{}", groupCode);
                    return true;
                }
            }
            log.info("RateLimitTimeout,instanceId:{}, queueGroupCode:{},", taskDO.getInstanceId(),
                    groupCode);
            return false; // 超时
        } catch (Exception e) {
            LoggerUtil.error(log, "waitForRateLimitRelease error, queueGroupCode:{}", groupCode, e);
            return false;
        }
    }

    public void processQueuesASync(QueueGroupDTO queueGroupDTO, List<QueueDetailDTO> queues, OutboundCallTaskDO taskDO,
                                   CallTimeRange groupCallTimeRange) {
        String instanceId = queueGroupDTO.getInstanceId();
        String queueGroupCode = queueGroupDTO.getQueueGroupCode();
        if (CollectionUtils.isEmpty(queues)) {
            LoggerUtil.info(log, "not find queues,groupCode:{},taskCode:{},instanceId:{}",
                    queueGroupCode, taskDO.getTaskCode(), taskDO.getInstanceId());
            completeGroup(queueGroupDTO, queues, System.currentTimeMillis());
            return;
        }
        boolean groupLock = redisLock.tryLock(CommonUtil.buildGroupLockKey(instanceId, queueGroupCode, env),
                "1", 60 * 10);
        if (!groupLock) {
            LoggerUtil.info(log, "outCall tryLock fail,instanceId:{}, queueGroupCode:{},", instanceId, queueGroupCode);
            return;
        }
        LoggerUtil.info(log, "doGroupOutCall start,instanceId:{},taskCode:{}, queueGroupCode:{}, queueSize:{}",
                instanceId, taskDO.getTaskCode(), queueGroupCode, queues.size());
        long startTime = System.currentTimeMillis();
        AtomicInteger completedTasks = new AtomicInteger(0);
        for (QueueDetailDTO queueDetail : queues) {
            try {
                ThreadPoolExecutor makeCallThreadPool = getThreadPoolExecutor(instanceId);
                if (makeCallThreadPool.getQueue().size() > outCallScheduleDrm.getMakeCallMaxQueueSize()) {
                    queueDetail.setStatus(QueueStatus.WAITING);
                    queueDetail.getExtInfo().put(OutCallResult.FAIL_REASON, OutCallResult.QUEUE_LIMIT);
                    queueDetailService.updateByCode(queueDetail);
                    LoggerUtil.info(log, "outCallQueueLimit,update to waiting,instanceId:{},taskCode:{}, queueGroupCode:{},queueCode:{}",
                            queueGroupDTO.getInstanceId(), queueGroupDTO.getTaskCode(), queueGroupCode, queueDetail.getQueueCode());
                    if (completedTasks.incrementAndGet() == queues.size()) {
                        completeGroup(queueGroupDTO, queues, startTime);
                        boolean result = redisLock.unlock(CommonUtil.buildGroupLockKey(instanceId, queueGroupCode, env), "1");
                        LoggerUtil.info(log, "outCallQueueLimit,outCall unlock success,instanceId:{}, queueGroupCode:{},result:{}",
                                instanceId, queueGroupCode, result);
                    }
                    continue;
                }
                makeCallThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        long startTime = System.currentTimeMillis();
                        // 随机扰乱-平滑限流
                        sleepQuietly((long) (Math.random() * 500));
                        try {
                            if (populateQueueInfoIfNotInCallTimeRange(groupCallTimeRange, queueDetail)) {
                                queueDetailService.updateByCode(queueDetail);
                                return;
                            }
                            populateCallResult(queueGroupDTO, makeCall(queueDetail, taskDO), queueDetail);
                            // 立即更新状态
                            queueDetailService.updateByCode(queueDetail);
                        } catch (Exception e) {
                            LoggerUtil.error(log, e, "processQueuesASync error,instanceId:{},taskCode:{},groupCode:{}" + " ,queueCode:{}",
                                    queueDetail.getInstanceId(), queueDetail.getTaskCode(), queueDetail.getGroupCode(),
                                    queueDetail.getQueueCode());
                            taskPlanService.replanExceptionTask(queueGroupDTO, Collections.singletonList(queueDetail.getQueueCode()));
                        } finally {
                            log.info("completeQueueOutCall,instanceId:{},taskCode:{},queueGroupCode:{}," +
                                            "queueCode:{},status:{},time:{}",
                                    instanceId, queueGroupDTO.getTaskCode(), queueGroupCode,
                                    queueDetail.getQueueCode(), queueDetail.getStatus(), System.currentTimeMillis() - startTime);
                            // 任务完成，检查是否所有任务都已完成
                            if (completedTasks.incrementAndGet() == queues.size()) {
                                completeGroup(queueGroupDTO, queues, startTime);
                                boolean result = redisLock.unlock(CommonUtil.buildGroupLockKey(instanceId, queueGroupCode, env), "1");
                                LoggerUtil.info(log, "completeQueueOutCall,outCall unlock success,instanceId:{}, queueGroupCode:{},result:{}", instanceId, queueGroupCode, result);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                // 提交异常，可能是线程池满了
                LoggerUtil.error(log, e, "submit task error,queueCode:{},taskId:{}",
                        queueDetail.getQueueCode(), queueDetail.getTaskCode());
                queueDetail.setStatus(QueueStatus.WAITING);
                queueDetail.getExtInfo().put(OutCallResult.FAIL_REASON, OutCallResult.POOL_FULL);
                queueDetailService.updateByCode(queueDetail);
                LoggerUtil.info(log, "update queue to waiting,queueCode:{},taskId:{}",
                        queueDetail.getQueueCode(), queueDetail.getTaskCode());
                if (completedTasks.incrementAndGet() == queues.size()) {
                    completeGroup(queueGroupDTO, queues, startTime);
                    boolean result = redisLock.unlock(CommonUtil.buildGroupLockKey(instanceId, queueGroupCode, env), "1");
                    LoggerUtil.info(log, "exception,outCall unlock success,instanceId:{}, queueGroupCode:{},result:{}",
                            instanceId, queueGroupCode, result);
                }
            }
        }
        LoggerUtil.info(log, "processQueuesASync,submitted all tasks,instanceId:{},taskCode:{},queueGroupCode:{},queueSize:{}",
                queueGroupDTO.getInstanceId(), queueGroupDTO.getTaskCode(), queueGroupCode, queues.size());
    }

    private boolean populateQueueInfoIfNotInCallTimeRange(CallTimeRange groupCallTimeRange, QueueDetailDTO queueDetail) {
        CallTimeRange.CallInfo callInfo = groupCallTimeRange.calCallTimeInfo();
        if (!callInfo.isInCurrentCallTimeRange()) {
            LoggerUtil.info(log, ",notMatchCallTime:{}, instanceId:{},queueGroupCode:{},queueCode:{},taskCode:{}",
                    CommonUtil.simplifyTimeRange(groupCallTimeRange), queueDetail.getInstanceId(),
                    queueDetail.getGroupCode(), queueDetail.getQueueCode(), queueDetail.getTaskCode());
            if (callInfo.isInTaskTimeRange()) {
                queueDetail.setStatus(QueueStatus.WAITING);
                queueDetail.getExtInfo().put(OutCallResult.FAIL_REASON, OutCallResult.NOT_MATCH_TIME);
            } else {
                // 不在任务时间范围内，更新队列状态为停止
                queueDetail.setStatus(QueueStatus.STOP);
                queueDetail.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.NOT_MATCH_TIME);
            }
            return true;
        }
        return false;
    }

    private void completeGroup(QueueGroupDTO queueGroupDTO, List<QueueDetailDTO> queues, long startTime) {
        queueGroupDTO.setGroupStatus(GroupStatus.STOP);
        queueGroupDTO.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.EXECUTE_FINISH);
        queueGroupService.updateQueueGroupStatus(queueGroupDTO);
        LoggerUtil.info(log, "processQueuesASync,all tasks completed,instanceId:{},taskCode:{},queueGroupCode:{},resultSize:{},consumeTime:{}",
                queueGroupDTO.getInstanceId(), queueGroupDTO.getTaskCode(), queueGroupDTO.getQueueGroupCode(), queues.size(),
                formatTime(System.currentTimeMillis() - startTime));
    }

    private void updatePoolConfigIfNeeded() {
        if (outCallScheduleDrm.getCommonMakeCallCorePoolSize() != commonMakeCallCorePoolSize) {
            log.info("update commonMakeCallCorePoolSize, old:{} , new:{}", commonMakeCallCorePoolSize,
                    outCallScheduleDrm.getCommonMakeCallCorePoolSize());
            commonMakeCallCorePoolSize = outCallScheduleDrm.getCommonMakeCallCorePoolSize();
            OutCallExecutorService.getCommonMakeCallThreadPool().setCorePoolSize(outCallScheduleDrm.getCommonMakeCallCorePoolSize());
        }
        if (outCallScheduleDrm.getOutCallExecutorMaxPoolSize() != makeCallMaxPoolSize) {
            log.info("update makeCallMaxPoolSize, old:{} , new:{}", makeCallMaxPoolSize,
                    outCallScheduleDrm.getOutCallExecutorMaxPoolSize());
            makeCallMaxPoolSize = outCallScheduleDrm.getOutCallExecutorMaxPoolSize();
            OutCallExecutorService.getLargeMakeCallThreadPool().setMaximumPoolSize(outCallScheduleDrm.getOutCallExecutorMaxPoolSize());
            OutCallExecutorService.getCommonMakeCallThreadPool().setMaximumPoolSize(outCallScheduleDrm.getOutCallExecutorMaxPoolSize());
        }
    }

    private void controlRequestRate() {
        try {
            if (outCallScheduleDrm.getRequestRateControl() > 0) {
                Thread.sleep(outCallScheduleDrm.getRequestRateControl());
            }
        } catch (Exception e) {
            log.error("controlRequestRate error", e);
        }
    }

    private void populateCallResult(QueueGroupDTO queueGroupDTO, OutCallResult callResult, QueueDetailDTO queue) {
        queue.setGroupCode(queueGroupDTO.getQueueGroupCode());
        queue.setAcid(callResult.getAcid());
        if (callResult.isSuccess()) {
            queue.setStatus(QueueStatus.PROCESSING);
        } else {
            String errorCode = callResult.getErrorCode();
            if (Objects.equals(errorCode, OutCallResult.STOP_REASON)) {
                queue.setStatus(QueueStatus.STOP);
                queue.getExtInfo().put(OutCallResult.STOP_REASON, callResult.getErrorMsg());
            }
            if (queue.getCallCount() >= outCallScheduleDrm.getMaxCallFlowRetries()) {
                queue.setStatus(QueueStatus.STOP);
                queue.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.MAX_RETRIES);
            } else if (Objects.equals(errorCode, OutCallResult.FAIL_REASON)) {
                queue.setStatus(QueueStatus.WAITING);
                queue.getExtInfo().put(OutCallResult.FAIL_REASON, callResult.getErrorMsg());
            }
        }
    }

    /**
     * 获取可外呼的主叫号码
     */
    public String getOutboundCaller(OutboundCallTaskDO taskDO) {
        // 获取可外呼的主叫号码
        List<String> outboundCallerList = new ArrayList<>();
        for (String caller : taskDO.getOutboundCaller().split(",")) {
            outboundCallerList.add(caller);
        }
        if (CollectionUtils.isEmpty(outboundCallerList)) {
            throw new RuntimeException("outboundCaller is empty");
        }
        return RandomUtil.chanceSelect(outboundCallerList);
    }

    /**
     * 发起外呼
     */
    private OutCallResult makeCall(QueueDetailDTO queue, OutboundCallTaskDO taskDO) {
        // 再重新查下保证数据的一致性
        taskDO = outboundCallTaskService.queryOneCallTaskByCode(taskDO.getInstanceId(), taskDO.getTaskCode());
        if (!TaskStatusEnum.allowCallUpgrade(taskDO.getTaskStatus())) {
            LoggerUtil.info(log, "task status is not allow call,instanceId:{}, taskCode{},taskStatus:{},queueCode:{}",
                    taskDO.getInstanceId(), queue.getTaskCode(), taskDO.getTaskStatus(), queue.getQueueCode());
            return OutCallResult.fail(OutCallResult.FAIL_REASON, OutCallResult.STATUS_INVALID);
        }
        String lockKey = taskDO.getInstanceId() + ":" + taskDO.getTaskCode() + ":" + queue.getCallee();
        try {
            String outboundCaller = getOutboundCaller(taskDO);
            LoggerUtil.info(log, "makeCall outboundCaller:{},{}", queue.getTaskCode(), outboundCaller);
            queue.setCaller(outboundCaller);
            if (interceptTodayRecallService.isTodayRecall(queue, taskDO)) {
                log.info("interceptTodayRecall,instanceId:{},taskCode:{},queueCode:{},callee:{}",
                        queue.getInstanceId(), queue.getTaskCode(), queue.getQueueCode(), queue.getCallee());
                return OutCallResult.fail(OutCallResult.STOP_REASON, OutCallResult.RECALL_REASON);
            }
            boolean tryLock = cacheClient.putNotExist(lockKey, "1", 30);
            if (!tryLock) {
                LoggerUtil.info(log, "makeCall tryLock fail,lockKey:{}", lockKey);
                return OutCallResult.fail(OutCallResult.STOP_REASON, "tryLock fail");
            }
            if (!waitForRateLimitRelease(queue.getGroupCode(), taskDO)) {
                LoggerUtil.info(log, "waitForRateLimitRelease,makecall fail" +
                                "instanceId:{},taskCode:{}, queueGroupCode:{},queueCode:{}",
                        queue.getInstanceId(), queue.getTaskCode(), queue.getGroupCode(), queue.getQueueCode());
                return OutCallResult.failForFlowLimit();
            }
            LoggerUtil.info(log, "executeMakeCall Start,instanceId:{},taskCode:{},queueCode:{}",
                    queue.getInstanceId(), queue.getTaskCode(), queue.getQueueCode());
            long currentTimeMillis = System.currentTimeMillis();
            MakeCallCoreRequest makeCallCoreRequest = new MakeCallCoreRequest();
            makeCallCoreRequest.setCaller(outboundCaller);
            makeCallCoreRequest.setCallee(queue.getCallee());
            makeCallCoreRequest.setInstanceId(queue.getInstanceId());
            makeCallCoreRequest.setQueueCode(queue.getQueueCode());
            makeCallCoreRequest.setTaskCode(queue.getTaskCode());
            // 5、发起外呼
            RPCResult<MakeCallResponse> rpcResult = remoteFsApi.makeCall(makeCallCoreRequest);
            LoggerUtil.info(log, "executeMakeCallEnd,instanceId:{},taskCode:{},groupCode:{},queueCode:{},success:{},timeConsume:{}",
                    queue.getInstanceId(), queue.getTaskCode(), queue.getGroupCode(), queue.getQueueCode(), rpcResult.getCode() == 200,
                    System.currentTimeMillis() - currentTimeMillis);
            if (rpcResult.getCode() != 200 || rpcResult.getData() == null) {
                LoggerUtil.info(log, "executeOutboundTaskCall error OutCallResult error,makeCallCoreRequest:{},groupCode:{},rpcResult:{},response:{}",
                        makeCallCoreRequest, queue.getGroupCode(), OutCallResult.UNKNOWN_ERROR, JSONObject.toJSONString(rpcResult));
                return OutCallResult.fail(OutCallResult.STOP_REASON, OutCallResult.UNKNOWN_ERROR);
            }
            if (rpcResult.getData().getFlowLimit()) {
                return OutCallResult.failForFlowLimit();
            }
            LoggerUtil.info(log, "makeCall OutCallResult success,makeCallCoreRequest :{},groupCode:{},rpcResult:{}", makeCallCoreRequest,
                    queue.getGroupCode(), JSON.toJSONString(rpcResult));
            if (rpcResult.getData() != null) {
                queue.setAcid(rpcResult.getData().getAcid());
                queue.setCallCount(queue.getCallCount() + 1);
                addCacheIfNeeded(queue);
                LoggerUtil.info(log, "outCall success,queueCode uniqueKey session makeCallCoreRequest:{}, taskCode groupCode:{},{},{}",
                        makeCallCoreRequest, queue.getTaskCode(), queue.getGroupCode());
                return OutCallResult.success(rpcResult.getData().getAcid());
            }
        } catch (Exception e) {
            LoggerUtil.error(log, e, "makeCallException, instanceId:{},taskCode:{},groupCode:{},queueCode:{},rpcResult:{} ",
                    queue.getInstanceId(), queue.getTaskCode(), queue.getGroupCode(), queue.getQueueCode());
        } finally {
            cacheClient.delete(lockKey);
        }
        return OutCallResult.fail(OutCallResult.STOP_REASON, OutCallResult.UNKNOWN_ERROR);
    }

    private void addCacheIfNeeded(QueueDetailDTO queue) {
        if (outCallScheduleDrm.getInterceptTodayRecallInstance().contains(queue.getInstanceId())) {
            // 计算缓存过期时间：从当前时间到晚上11点，加上0-10分钟的随机数，避免集中失效
            DateTime now = new DateTime();
            DateTime tonight11pm = now.withTimeAtStartOfDay().plusHours(23); // 晚上11点
            // 如果当前时间已经超过晚上11点，
            if (now.isAfter(tonight11pm)) {
                return;
            }
            // 计算到今晚11点的秒数
            int secondsUntil11pm = (int) ((tonight11pm.getMillis() - now.getMillis()) / 1000);
            // 添加0-10分钟(0-600秒)的随机数
            int randomSeconds = (int) (Math.random() * 600);
            int cacheExpireSeconds = secondsUntil11pm + randomSeconds;
            // 计算准确的过期时间
            DateTime actualExpireTime = now.plusSeconds(cacheExpireSeconds);
            String key = CommonUtil.buildPhoneCacheKey(queue);
            boolean success = cacheClient.putNotExist(key, "1", cacheExpireSeconds);
            LoggerUtil.info(log, "addPhone2Cache,queue:{},Phone:{} add to cache:{}, key:{},actualExpireTime:{}",
                    queue.getQueueCode(), queue.getCallee(), success, key, actualExpireTime.toString("yyyy-MM-dd HH:mm:ss"));
        }
    }

    private void sleepQuietly(long millis) {
        try {
            if (millis > 0) {
                Thread.sleep(millis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted", e);
        }
    }

    /**
     * 格式化耗时显示
     *
     * @param millis 耗时（毫秒）
     * @return 格式化后的字符串
     */
    private String formatTime(long millis) {
        if (millis >= 3600000) { // 大于等于1小时
            return String.format("%.2f小时", millis / 3600000.0);
        } else if (millis >= 60000) { // 大于等于1分钟
            return String.format("%.2f分钟", millis / 60000.0);
        } else {
            return millis + "毫秒";
        }
    }
}