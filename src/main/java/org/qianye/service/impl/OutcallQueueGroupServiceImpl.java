package org.qianye.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.joda.time.DateTime;
import org.qianye.DTO.*;
import org.qianye.cache.CacheClient;
import org.qianye.cache.QueueGroupRedisCache;
import org.qianye.cache.RedisLock;
import org.qianye.common.*;
import org.qianye.engine.OutCallExecutorService;
import org.qianye.engine.OutCallService;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.entity.OutcallQueueGroupDO;
import org.qianye.mapper.OutcallQueueGroupMapper;
import org.qianye.service.OutboundCallTaskService;
import org.qianye.service.OutcallQueueGroupService;
import org.qianye.service.OutcallQueueService;
import org.qianye.util.CommonUtil;
import org.qianye.util.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OutcallQueueGroupServiceImpl extends ServiceImpl<OutcallQueueGroupMapper, OutcallQueueGroupDO>
        implements OutcallQueueGroupService {
    @Resource
    private OutcallQueueService queueDetailService;
    @Resource
    private OutboundCallTaskService outboundCallTaskService;
    @Resource
    private OutCallService outCallService;
    @Resource
    private RedisLock redisLock;
    @Resource
    private OutCallScheduleDrm outCallScheduleDrm;
    @Resource
    private TaskPlanService taskPlanService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private QueueGroupRedisCache queueGroupRedisCache;
    @Value("${env}")
    private String env;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void checkGroupStatus() {
        try {
            // 超时自动释放
            boolean tryLock = cacheClient.putNotExist("checkGroupStatus:" + env, "1", 60);
            if (!tryLock) {
                LoggerUtil.info(log, "checkGroupStatus get lock fail,ignore it");
                return;
            }
            LoggerUtil.info(log, "checkGroupStatus start");
            // 分页处理所有进行中的任务
            int pageNum = 1;
            int pageSize = 200;
            PageDTO<OutboundCallTaskDO> processingTaskPage;
            do {
                processingTaskPage = outboundCallTaskService.queryProcessingTaskList(pageNum, pageSize);
                if (CollectionUtils.isEmpty(processingTaskPage.getRecords())) {
                    LoggerUtil.info(log, "checkProcessingGroup no processing task on page {}, ignore it", pageNum);
                    break;
                }
                for (OutboundCallTaskDO task : processingTaskPage.getRecords()) {
                    CallTimeRange callTimeRange = taskPlanService.loadTaskCallTimeRange(task.getInstanceId(), task.getTaskCode());
                    if (callTimeRange.isInCallTimeRange()) {
                        LoggerUtil.info(log, "task in call time instanceId:{},task:{}",
                                task.getInstanceId(), task.getTaskCode());
                        CompletableFuture.runAsync(new TracerRunnable() {
                            @Override
                            public void doRun() {
                                startPlanningGroup(task, false); // 处理普通组（NORMAL和RETRY）
                            }
                        }, OutCallExecutorService.getQueueGroupthreadPool());
                        CompletableFuture.runAsync(new TracerRunnable() {
                            @Override
                            public void doRun() {
                                startPlanningGroup(task, true); // 处理择时组（FIXED_TIME）
                            }
                        }, OutCallExecutorService.getQueueGroupthreadPool());
                        CompletableFuture.runAsync(new TracerRunnable() {
                            @Override
                            public void doRun() {
                                handleProcessingGroup(task);
                            }
                        }, OutCallExecutorService.getQueueGroupthreadPool());
                    } else {
                        LoggerUtil.info(log, "groupPlan notInCallTime, instanceId:{},task:{},timerange:{}",
                                task.getInstanceId(), task.getTaskCode(), CommonUtil.simplifyTimeRange(callTimeRange));
                    }
                }
                // 处理下一页
                pageNum++;
            } while (processingTaskPage.getRecords().size() == pageSize); // 当返回记录数小于pageSize时，说明已经处理完所有数据
        } catch (Exception e) {
            LoggerUtil.error(log, "checkProcessingGroup error", e);
        }
    }

    /**
     * 统一处理普通组和择时组的规划
     *
     * @param task             任务信息
     * @param isFixedTimeGroup 是否为择时组
     */
    @Override
    public void startPlanningGroup(OutboundCallTaskDO task, boolean isFixedTimeGroup) {
        String instanceId = task.getInstanceId();
        String taskCode = task.getTaskCode();
        String lockKey = isFixedTimeGroup ?
                "startPlanningFixedGroup:" + taskCode + ":" + env :
                "startPlanningGroup:" + taskCode + ":" + env;
        boolean tryLock = redisLock.tryLock(lockKey, "1", 40);
        if (!tryLock) {
            LoggerUtil.info(log, "{} get lock fail,instanceId:{},taskCode:{},ignore it",
                    isFixedTimeGroup ? "startPlanningFixedGroup" : "startPlanningGroup",
                    instanceId, taskCode);
            return;
        }
        LoggerUtil.info(log, "{} start,instanceId:{},taskCode:{}",
                isFixedTimeGroup ? "startPlanningFixedGroup" : "startPlanningGroup",
                instanceId, taskCode);
        try {
            int maxIterations = 1000; // 防止无限循环的最大迭代次数
            AtomicInteger iterationCount = new AtomicInteger();
            while (iterationCount.get() < maxIterations) {
                List<QueueGroupDTO> waitingGroups;
                if (isFixedTimeGroup) {
                    waitingGroups = queryFixedGroups(instanceId, taskCode);
                } else {
                    waitingGroups = queryNormalWaitingGroups(instanceId, taskCode);
                }
                if (CollectionUtils.isEmpty(waitingGroups)) {
                    LoggerUtil.info(log, "{} no waiting groups,instanceId:{},taskCode:{}",
                            isFixedTimeGroup ? "startPlanningFixedGroup" : "startPlanningGroup",
                            instanceId, taskCode);
                    break;
                }
                boolean reachedCacheMaxSize = queueGroupRedisCache.isReachedCacheMaxSize(instanceId, taskCode, env, isFixedTimeGroup);
                if (reachedCacheMaxSize) {
                    // 睡10s 再检查一次
                    Thread.sleep(10 * 1000);
                    reachedCacheMaxSize = queueGroupRedisCache.isReachedCacheMaxSize(instanceId, taskCode, env, isFixedTimeGroup);
                    if (reachedCacheMaxSize) {
                        LoggerUtil.warn(log, ",{},waitingGroupsCacheLimit," +
                                        "instanceId:{},taskCode:{}",
                                isFixedTimeGroup ? "startPlanningFixedGroup" : "startPlanningGroup",
                                task.getInstanceId(), task.getTaskCode());
                        break;
                    }
                }
                List<String> groupCodes = waitingGroups.stream().map(QueueGroupDTO::getQueueGroupCode).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(groupCodes)) {
                    LoggerUtil.info(log, "All groups have invalid status, skipping this batch");
                    break;
                }
                waitingGroups.forEach(group -> group.setGroupStatus(GroupStatus.PLANNING));
                updateQueueGroupStatus(waitingGroups);
                log.info("updateQueueGroupStatus to PLANNING success,instanceId:{},taskCode:{},groupCodes:{}",
                        instanceId, taskCode, groupCodes);
                boolean success = queueGroupRedisCache.addGroupFromLeft(
                        instanceId,
                        taskCode,
                        env,
                        groupCodes, isFixedTimeGroup);
                if (success) {
                    iterationCount.getAndIncrement();
                    LoggerUtil.info(log, ",GroupPlanSuccess, to cache " +
                                    ",instanceId:{},taskCode:{},normalGroup:{},groupSize:{},groups:{}",
                            instanceId, taskCode, !isFixedTimeGroup, groupCodes.size(), groupCodes);
                    CompletableFuture.runAsync(new TracerRunnable() {
                        @Override
                        public void doRun() {
                            outCallService.executeGroupOutCall(task);
                        }
                    }, OutCallExecutorService.getOutCallThreadPool());
                } else {
                    LoggerUtil.error(log, ",{},GroupPlan fail," +
                                    "instanceId:{},taskCode:{},groupCodes:{}",
                            isFixedTimeGroup ? "startPlanningFixedGroup" : "startPlanningGroup",
                            task.getInstanceId(), task.getTaskCode(), groupCodes);
                    break; // 添加失败则停止处理
                }
            }
            if (iterationCount.get() >= maxIterations) {
                LoggerUtil.warn(log, ",{} ,iterationsLimit: {}, instanceId:{}, taskCode:{}",
                        isFixedTimeGroup ? "startPlanningFixedGroup," : "startPlanningGroup,",
                        maxIterations, task.getInstanceId(), task.getTaskCode());
            }
        } catch (Exception e) {
            LoggerUtil.error(log, ",GroupPlanError,{},instanceId:{},taskCode:{},",
                    isFixedTimeGroup ? "startPlanningFixedGroup" : "startPlanningGroup",
                    task.getInstanceId(), task.getTaskCode(), e);
        } finally {
            redisLock.unlock(lockKey, "1");
        }
    }

    private List<QueueGroupDTO> queryFixedGroups(String instanceId, String taskCode) {
        List<QueueGroupDTO> waitingGroups;
        // 查询当前时间段的择时名单组
        QueueGroupRequest queueGroupRequest = new QueueGroupRequest();
        queueGroupRequest.setInstanceId(instanceId);
        queueGroupRequest.setTaskCode(taskCode);
        queueGroupRequest.setGroupStatus(GroupStatus.WAITING);
        queueGroupRequest.setGroupTypes(Collections.singletonList(QueueGroupType.FIXED_TIME.name()));
        queueGroupRequest.setGroupStartTime(DateTime.now().hourOfDay().roundFloorCopy().toDate());
        // 分页查询, 如果数据较多，则需要增加查询数量，以及增加定时任务的执行频率
        queueGroupRequest.setPageSize(outCallScheduleDrm.getPlanningGroupQuerySize());
        queueGroupRequest.setPageNum(1);
        waitingGroups = pageQueueGroup(queueGroupRequest).getList();
        return waitingGroups;
    }

    private List<QueueGroupDTO> queryNormalWaitingGroups(String instanceId, String taskCode) {
        DateTime now = new DateTime();
        DateTime startOfDay = now.withTimeAtStartOfDay();
        DateTime endOfDay = startOfDay.plusDays(1);
        QueueGroupRequest groupRequest = new QueueGroupRequest();
        groupRequest.setGroupStatus(GroupStatus.WAITING);
        groupRequest.setGroupTypes(Arrays.asList(QueueGroupType.NORMAL.name(), QueueGroupType.RETRY.name()));
        groupRequest.setInstanceId(instanceId);
        groupRequest.setTaskCode(taskCode);
        groupRequest.setUpdateTimeStart(startOfDay.minusDays(1).toDate());
        groupRequest.setUpdateTimeEnd(endOfDay.toDate());
        groupRequest.setPageNum(1);
        groupRequest.setPageSize(outCallScheduleDrm.getPlanningGroupQuerySize());
        return pageQueueGroup(groupRequest).getList();
    }

    /**
     * 处理状态为processing
     *
     * @param task
     */
    private void handleProcessingGroup(OutboundCallTaskDO task) {
        String lockKey = "checkProcessingGroup:" + task.getTaskCode() + ":" + env;
        boolean tryLock = redisLock.tryLock(lockKey, "1", 60 * 2);
        if (!tryLock) {
            LoggerUtil.info(log, "checkProcessingGroup get lock fail,instanceId:{},taskCode:{},ignore it",
                    task.getInstanceId(), task.getTaskCode());
            return;
        }
        LoggerUtil.info(log, "checkProcessingGroup start,instanceId:{},taskCode:{}",
                task.getInstanceId(), task.getTaskCode());
        try {
            QueueGroupRequest groupRequest = new QueueGroupRequest();
            groupRequest.setGroupStatus(GroupStatus.PROCESSING);
            groupRequest.setInstanceId(task.getInstanceId());
            groupRequest.setTaskCode(task.getTaskCode());
            groupRequest.setPageNum(1);
            groupRequest.setPageSize(100);
            DateTime dateTime = new DateTime();
            // 查询60分钟前的processing组
            groupRequest.setUpdateTimeStart(dateTime.withTimeAtStartOfDay().toDate());
            groupRequest.setUpdateTimeEnd(dateTime.minusMinutes(outCallScheduleDrm.getQueueGroupTimeAge()).toDate());
            List<QueueGroupDTO> processing = pageQueueGroup(groupRequest).getList();
            if (CollectionUtils.isEmpty(processing)) {
                return;
            }
            LoggerUtil.info(log, "checkProcessingGroup processing group:{},instanceId:{},taskCode:{}",
                    processing.stream().map(QueueGroupDTO::getQueueCodes).collect(Collectors.toList()),
                    task.getInstanceId(), task.getTaskCode());
            for (QueueGroupDTO queueGroupDTO : processing) {
                // 检查这个组所属的机器是否还在正常处理，检查该组的锁是否还在来确认
                if (isAlive(queueGroupDTO)) {
                    LoggerUtil.info(log, "checkProcessingGroup group is alive,groupCode:{}", queueGroupDTO.getQueueGroupCode());
                    continue;
                }
                LoggerUtil.warn(log, "checkProcessingGroup group is not alive,instanceId:{},taskCode:{},groupCode:{}",
                        queueGroupDTO.getInstanceId(), queueGroupDTO.getTaskCode(), queueGroupDTO.getQueueGroupCode());
                List<QueueDetailDTO> detailList = queueDetailService.getByCodes(queueGroupDTO.getInstanceId(), queueGroupDTO.getQueueCodes());
                LoggerUtil.warn(log, ",checkProcessingGroup,groupCode:{},failedQueues:{}",
                        queueGroupDTO.getQueueGroupCode(),
                        JSON.toJSONString(detailList.stream().map(QueueDetailDTO::getQueueCode)
                                .collect(Collectors.toList())));
                queueGroupDTO.setGroupStatus(GroupStatus.STOP);
                queueGroupDTO.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.MACHINE_DOWN);
                updateQueueGroupStatus(queueGroupDTO);
                CompletableFuture.runAsync(() -> {
                    taskPlanService.generateRetryGroup(detailList, queueGroupDTO);
                });
            }
        } catch (Exception e) {
            LoggerUtil.error(log, "checkProcessingGroup error", e);
        } finally {
            redisLock.unlock(lockKey, "1");
        }
    }

    private boolean isAlive(QueueGroupDTO queueGroupDTO) {
        String lockKey = CommonUtil.buildGroupLockKey(queueGroupDTO.getInstanceId(), queueGroupDTO.getQueueGroupCode(), queueGroupDTO.getEnvId());
        return cacheClient.exists(lockKey);
    }

    @Override
    public PageData<List<QueueGroupDTO>> pageQueueGroup(QueueGroupRequest request) {
        LoggerUtil.info(log, "pageQueueGroup request:{}", JSON.toJSONString(request));
        LambdaQueryWrapper<OutcallQueueGroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getInstanceId, request.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getTaskCode, request.getTaskCode());
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getEnvId, env);
        if (StringUtils.isNotBlank(request.getGroupCode())) {
            lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupCode, request.getGroupCode());
        }
        lambdaQueryWrapper.in(!CollectionUtils.isEmpty(request.getGroupTypes()),
                OutcallQueueGroupDO::getGroupType, request.getGroupTypes());
        if (request.getGroupStatus() != null) {
            lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupStatus, request.getGroupStatus().name());
        }
        if (request.getUpdateTimeStart() != null) {
            lambdaQueryWrapper.ge(OutcallQueueGroupDO::getGmtModified, request.getUpdateTimeStart());
        }
        if (request.getUpdateTimeEnd() != null) {
            lambdaQueryWrapper.le(OutcallQueueGroupDO::getGmtModified, request.getUpdateTimeEnd());
        }
        if (request.getGroupStartTime() != null) {
            lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupStartTime, request.getGroupStartTime());
        }
        lambdaQueryWrapper.orderByAsc(OutcallQueueGroupDO::getGmtModified);
        PageDTO<OutcallQueueGroupDO> pageQuery = new PageDTO<>(request.getPageNum(), request.getPageSize());
        PageDTO<OutcallQueueGroupDO> pageDTO = baseMapper.selectPage(pageQuery, lambdaQueryWrapper);
        if (!CollectionUtils.isEmpty(pageDTO.getRecords())) {
            return new PageData<>(convert2DTO(pageDTO.getRecords()), pageDTO.getCurrent(), pageDTO.getSize(), pageDTO.getTotal());
        }
        return new PageData<>(Collections.emptyList(), pageDTO.getCurrent(), pageDTO.getSize(), pageDTO.getTotal());
    }

    private List<QueueGroupDTO> convert2DTO(List<OutcallQueueGroupDO> records) {
        return records.stream().map(this::convert2DTO).collect(Collectors.toList());
    }

    @Override
    public QueueGroupDTO queryQueueGroupByCode(String instanceId, String queueGroupCode) {
        LoggerUtil.info(log, "queryQueueGroupByCode instanceId:{},queueGroupCode:{}", instanceId, queueGroupCode);
        LambdaQueryWrapper<OutcallQueueGroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getInstanceId, instanceId);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupCode, queueGroupCode);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getEnvId, env);
        OutcallQueueGroupDO queueGroupDO = baseMapper.selectOne(lambdaQueryWrapper);
        if (queueGroupDO != null && StringUtils.isNotBlank(queueGroupDO.getQueueCodes())) {
            QueueGroupDTO groupDTO = convert2DTO(queueGroupDO);
            return groupDTO;
        }
        return null;
    }

    @Override
    public OutcallQueueGroupDO getByInstanceAndCode(String instanceId, String envId, String groupCode) {
        return null;
    }

    @Override
    public Page<OutcallQueueGroupDO> pageByTask(String instanceId, String taskCode, String envId, int pageNum, int pageSize) {
        return null;
    }

    @Override
    public boolean updateStatus(String instanceId, String envId, String groupCode, String status) {
        return false;
    }

    @Override
    public List<QueueGroupDTO> queryQueueGroupByCodes(String instanceId, List<String> queueGroupCodes) {
        LoggerUtil.info(log, "queryQueueGroupByCodes instanceId:{},queueGroupCodes:{}", instanceId, queueGroupCodes);
        LambdaQueryWrapper<OutcallQueueGroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getInstanceId, instanceId);
        lambdaQueryWrapper.in(OutcallQueueGroupDO::getGroupCode, queueGroupCodes);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getEnvId, env);
        List<OutcallQueueGroupDO> queueGroupDO = baseMapper.selectList(lambdaQueryWrapper);
        if (!CollectionUtils.isEmpty(queueGroupDO)) {
            return convert2DTO(queueGroupDO);
        }
        return Collections.emptyList();
    }

    private QueueGroupDTO convert2DTO(OutcallQueueGroupDO queueGroupDO) {
        String queueCodes = queueGroupDO.getQueueCodes();
        QueueGroupDTO groupDTO = QueueGroupDTO.builder()
                .queueGroupCode(queueGroupDO.getGroupCode())
                .queueCodes(Arrays.asList(queueCodes.split(",")))
                .groupType(QueueGroupType.valueOf(queueGroupDO.getGroupType().toUpperCase()))
                .groupStatus(GroupStatus.valueOf(queueGroupDO.getGroupStatus().toUpperCase()))
                .envId(queueGroupDO.getEnvId()).taskCode(queueGroupDO.getTaskCode())
                .instanceId(queueGroupDO.getInstanceId())
                .build();
        LoggerUtil.info(log, "convert2DTO queueGroupDO:{},{}",
                queueGroupDO.getGroupCode(), queueGroupDO.getExtInfo());
        if (StringUtils.isNotEmpty(queueGroupDO.getExtInfo())) {
            groupDTO.setExtInfo(JSON.parseObject(queueGroupDO.getExtInfo(), new TypeReference<Map<String, Object>>() {
            }));
        } else {
            groupDTO.setExtInfo(new HashMap<>());
        }
        return groupDTO;
    }

    @Override
    public void insertQueueGroup(List<QueueGroupDTO> queueGroupDTOList) {
        if (CollectionUtils.isEmpty(queueGroupDTOList)) {
            return;
        }
        LoggerUtil.info(log, "insertQueueGroup request:{}", queueGroupDTOList.size());
        for (QueueGroupDTO queueGroupDTO : queueGroupDTOList) {
            OutcallQueueGroupDO queueGroupDO = new OutcallQueueGroupDO();
            queueGroupDO.setGroupCode(queueGroupDTO.getQueueGroupCode());
            queueGroupDO.setTaskCode(queueGroupDTO.getTaskCode());
            queueGroupDO.setGroupType(queueGroupDTO.getGroupType().name());
            queueGroupDO.setGroupStatus(GroupStatus.WAITING.name());
            queueGroupDO.setEnvId(env);
            queueGroupDO.setExtInfo(JSON.toJSONString(queueGroupDTO.getExtInfo()));
            queueGroupDO.setInstanceId(queueGroupDTO.getInstanceId());
            queueGroupDO.setQueueCodes(String.join(",", queueGroupDTO.getQueueCodes()));
            queueGroupDO.setGroupStartTime(queueGroupDTO.getGroupStartTime());
            queueGroupDO.setGmtModified(new Date());
            baseMapper.insert(queueGroupDO);
        }
    }

    @Override
    public void addQueueGroup2Max(List<QueueGroupDTO> queueGroups) {
        insertQueueGroup(queueGroups);
    }

    public QueueGroupDTO queryLastestFixTimeGroup(String instanceId, String taskCode, Date thisTime) {
        LambdaQueryWrapper<OutcallQueueGroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getInstanceId, instanceId);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getTaskCode, taskCode);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupStatus, GroupStatus.WAITING.name());
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getEnvId, env);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupStartTime, thisTime);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupType, QueueGroupType.FIXED_TIME.name());
        lambdaQueryWrapper.orderByDesc(OutcallQueueGroupDO::getGmtModified);
        lambdaQueryWrapper.last(" limit 1");
        OutcallQueueGroupDO queueGroupDO = baseMapper.selectOne(lambdaQueryWrapper);
        if (queueGroupDO != null) {
            return convert2DTO(queueGroupDO);
        }
        return null;
    }

    /**
     * 可更新队列组所有字段
     *
     * @param existingGroup
     */
    @Override
    public void updateQueueByGroupCode(QueueGroupDTO existingGroup) {
        OutcallQueueGroupDO queueGroupDO = JSONObject.parseObject(JSONObject.toJSONString(existingGroup),
                new TypeReference<OutcallQueueGroupDO>() {
                });
        if (existingGroup.getExtInfo() != null) {
            queueGroupDO.setExtInfo(JSON.toJSONString(existingGroup.getExtInfo()));
        }
        queueGroupDO.setGroupType(existingGroup.getGroupType().name());
        queueGroupDO.setGroupStatus(existingGroup.getGroupStatus().name());
        queueGroupDO.setGmtModified(new Date());
        if (!CollectionUtils.isEmpty(existingGroup.getQueueCodes())) {
            queueGroupDO.setQueueCodes(String.join(",", existingGroup.getQueueCodes()));
        }
        LambdaQueryWrapper<OutcallQueueGroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getInstanceId, existingGroup.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupCode, existingGroup.getQueueGroupCode());
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getEnvId, env);
        baseMapper.update(queueGroupDO, lambdaQueryWrapper);
    }

    /**
     * 只更新状态和扩展信息
     *
     * @param request
     */
    @Transactional
    @Override
    public void updateQueueGroupStatus(List<QueueGroupDTO> request) {
        if (CollectionUtils.isEmpty(request)) {
            return;
        }
        log.info("updateQueueGroupStatus instanceId:{},taskCode{},status:{}", request.get(0).getInstanceId(), request.get(0).getTaskCode(), request.get(0).getGroupStatus());
        for (QueueGroupDTO queueGroupDTO : request) {
            update(new LambdaUpdateWrapper<OutcallQueueGroupDO>()
                    .eq(OutcallQueueGroupDO::getInstanceId, queueGroupDTO.getInstanceId())
                    .eq(OutcallQueueGroupDO::getGroupCode, queueGroupDTO.getQueueGroupCode())
                    .eq(OutcallQueueGroupDO::getEnvId, env)
                    .set(OutcallQueueGroupDO::getGroupStatus, queueGroupDTO.getGroupStatus().name())
                    .set(OutcallQueueGroupDO::getExtInfo, JSON.toJSONString(queueGroupDTO.getExtInfo()))
                    .set(OutcallQueueGroupDO::getGmtModified, new Date()));
        }
    }

    @Override
    public int updateQueueGroupStatus(QueueGroupDTO request) {
        OutcallQueueGroupDO queueGroupDO = new OutcallQueueGroupDO();
        queueGroupDO.setGroupStatus(request.getGroupStatus().name());
        queueGroupDO.setExtInfo(JSON.toJSONString(request.getExtInfo()));
        LambdaQueryWrapper<OutcallQueueGroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getInstanceId, request.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupCode, request.getQueueGroupCode());
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getEnvId, env);
        return baseMapper.update(queueGroupDO, lambdaQueryWrapper);
    }

    @Override
    public int updateQueueGroupStatus(String instanceId, String groupCode, GroupStatus status,
                                      Map<String, Object> extInfo) {
        OutcallQueueGroupDO queueGroupDO = new OutcallQueueGroupDO();
        queueGroupDO.setGroupStatus(status.name());
        if (MapUtils.isNotEmpty(extInfo)) {
            queueGroupDO.setExtInfo(JSON.toJSONString(extInfo));
        }
        LambdaQueryWrapper<OutcallQueueGroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getInstanceId, instanceId);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getGroupCode, groupCode);
        lambdaQueryWrapper.eq(OutcallQueueGroupDO::getEnvId, env);
        return baseMapper.update(queueGroupDO, lambdaQueryWrapper);
    }

    @Override
    @Transactional
    public void stopQueueGroupAndQueue(QueueGroupDTO groupDTO) {
        if (Objects.isNull(groupDTO) || CollectionUtils.isEmpty(groupDTO.getQueueCodes())) {
            return;
        }
        List<QueueDetailDTO> updateList = new ArrayList<>(groupDTO.getQueueCodes().size());
        // 创建批量更新的DTO列表
        for (String queueCode : groupDTO.getQueueCodes()) {
            QueueDetailDTO queueDetailDTO = new QueueDetailDTO();
            queueDetailDTO.setQueueCode(queueCode);
            queueDetailDTO.setGroupCode(groupDTO.getQueueGroupCode());
            queueDetailDTO.setStatus(QueueStatus.STOP);
            queueDetailDTO.setInstanceId(groupDTO.getInstanceId());
            Map<String, Object> queueDetailExtInfo = queueDetailDTO.getExtInfo();
            if (queueDetailExtInfo == null) {
                queueDetailExtInfo = new HashMap<>();
            }
            queueDetailExtInfo.put(OutCallResult.STOP_REASON, groupDTO.getExtInfo().get(OutCallResult.STOP_REASON));
            queueDetailDTO.setExtInfo(queueDetailExtInfo);
            updateList.add(queueDetailDTO);
        }
        groupDTO.setGroupStatus(GroupStatus.STOP);
        updateQueueGroupStatus(groupDTO);
        // 批量更新队列状态
        queueDetailService.updateQueues(updateList);
        LoggerUtil.info(log, "stopQueueGroupAndQueue,instanceId:{}, groupCode:{},queueCodes:{}",
                groupDTO.getInstanceId(), groupDTO.getQueueGroupCode(), JSON.toJSONString(groupDTO.getQueueCodes()));
    }

    @Override
    public void checkFixedGroup() {
        try {
            // 加锁 10分钟，防止重复执行，自动释放
            boolean exist = cacheClient.putNotExist("checkFixedTimingGroup:" + env, "1", 10 * 60);
            if (!exist) {
                LoggerUtil.info(log, "checkFixedTimingGroup get lock fail,ignore it");
                return;
            }
            LoggerUtil.info(log, "checkFixedTimingGroup start");
            // 分页处理所有进行中的任务
            int pageNum = 1;
            int pageSize = 200;
            PageDTO<OutboundCallTaskDO> processingTaskPage;
            do {
                processingTaskPage = outboundCallTaskService.queryProcessingTaskList(pageNum, pageSize);
                if (CollectionUtils.isEmpty(processingTaskPage.getRecords())) {
                    LoggerUtil.info(log, "checkFixedTimingGroup no processing task on page {}, ignore it", pageNum);
                    break;
                }
                for (OutboundCallTaskDO task : processingTaskPage.getRecords()) {
                    // 查询哪些没有完成的
                    QueueGroupRequest queueGroupRequest = new QueueGroupRequest();
                    queueGroupRequest.setGroupTypes(Collections.singletonList(QueueGroupType.FIXED_TIME.name()));
                    queueGroupRequest.setGroupStatus(GroupStatus.WAITING);
                    queueGroupRequest.setInstanceId(task.getInstanceId());
                    queueGroupRequest.setTaskCode(task.getTaskCode());
                    queueGroupRequest.setPageSize(pageSize);
                    queueGroupRequest.setGroupStartTime(new DateTime().minusHours(1).toDate());
                    List<QueueGroupDTO> notHandledGroups = this.pageQueueGroup(queueGroupRequest).getList();
                    if (!CollectionUtils.isEmpty(notHandledGroups)) {
                        // 监控
                        for (QueueGroupDTO queueGroupDTO : notHandledGroups) {
                            queueGroupDTO.setGroupStatus(GroupStatus.STOP);
                            queueGroupDTO.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.OUT_FIXED_TIME);
                        }
                        updateQueueGroupStatus(notHandledGroups);
                        LoggerUtil.info(log, "checkFixedTimingGroup, stop group:{}", JSON.toJSONString(notHandledGroups));
                        for (QueueGroupDTO queueGroupDTO : notHandledGroups) {
                            CompletableFuture.runAsync(() -> taskPlanService.generateRetryGroup(queueGroupDTO));
                        }
                    }
                }
                // 处理下一页
                pageNum++;
            } while (processingTaskPage.getRecords().size() == pageSize); // 当返回记录数小于pageSize时，说明已经处理完所有数据
        } catch (Exception e) {
            LoggerUtil.error(log, "createDummyLogContext error", e);
        }
    }
}