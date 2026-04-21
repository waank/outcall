package org.qianye.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.qianye.DTO.*;
import org.qianye.cache.QueueGroupRedisCache;
import org.qianye.cache.RedisLock;
import org.qianye.common.*;
import org.qianye.engine.OutCallExecutorService;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.entity.OutboundCallTaskRulesDO;
import org.qianye.entity.OutboundTimingInfoDO;
import org.qianye.service.*;
import org.qianye.util.CommonUtil;
import org.qianye.util.LoggerUtil;
import org.qianye.util.UuidUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskPlanService {
    @Resource
    protected QueueGroupRedisCache queueGroupRedisCache;
    @Resource
    private OutcallQueueService queueDetailService;
    @Resource
    private OutcallQueueGroupService queueGroupService;
    @Resource
    private OutboundCreateTaskTimingInfoService timingInfoService;
    @Resource
    private RedisLock redisLock;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Value("${env}")
    private String env;
    @Value("${app.cache.type:local}")
    private String cacheType;
    // 本地缓存用于存储任务时间范围
    private final Map<String, String> localCache = new ConcurrentHashMap<>();
    @Resource
    private OutboundCallTaskService outboundCallTaskService;
    @Resource
    private OutboundCallTaskRulesService outboundCallTaskRulesService;
    @Resource
    private CallRecordService callRecordService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private OutCallScheduleDrm outCallScheduleDrm;

    /**
     * 判断是否使用本地缓存
     */
    private boolean useLocalCache() {
        return "local".equalsIgnoreCase(cacheType);
    }

    /**
     * 构建任务时间范围缓存key
     */
    private String buildTaskCallTimeRangeCacheKey(String instanceId, String taskCode) {
        return "outboundTask:callTimeRange:" + instanceId + ":" + taskCode + ":" + env;
    }

    /**
     * 序列化CallTimeRange对象为JSON字符串
     */
    private String serializeCallTimeRange(CallTimeRange callTimeRange) {
        if (callTimeRange == null || CollectionUtils.isEmpty(callTimeRange.getCallTimes())) {
            return null;
        }
        try {
            return JSON.toJSONString(callTimeRange);
        } catch (Exception e) {
            LoggerUtil.error(log, "Failed to serialize CallTimeRange", e);
            return null;
        }
    }

    /**
     * 反序列化JSON字符串为CallTimeRange对象
     */
    private CallTimeRange deserializeCallTimeRange(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, CallTimeRange.class);
        } catch (Exception e) {
            LoggerUtil.error(log, "Failed to deserialize CallTimeRange", e);
            return null;
        }
    }

    public void replanExceptionTask(QueueGroupDTO queueGroupDTO, List<String> queueCodes) {
        LoggerUtil.info(log, ",exceptionReplanTask, queueGroupDTO: {}, queueCodes: {}", queueGroupDTO, queueCodes);
        if (queueGroupDTO == null || CollectionUtils.isEmpty(queueCodes)) {
            return;
        }
        try {
            if (shouldStopCurrentGroup(queueGroupDTO)) {
                if (queueGroupDTO.getGroupStatus() != GroupStatus.STOP) {
                    queueGroupService.stopQueueGroupAndQueue(queueGroupDTO);
                }
                LoggerUtil.warn(log, ",stopQueueGroupCode:{},stop queueCode:{}, timeRange:{}", queueGroupDTO.getQueueCodes(), CommonUtil.simplifyTimeRange(queueGroupDTO.getCallTimeRange()));
                return;
            }
            // 延迟2s，等待队列明细更新
            try {
                Thread.sleep(2 * 1000);
            } catch (InterruptedException e) {
                LoggerUtil.error(log, "replanTask error", e);
            }
            List<QueueDetailDTO> details = queueDetailService.getByCodes(queueGroupDTO.getInstanceId(), queueCodes);
            generateRetryGroup(details, queueGroupDTO);
        } catch (Exception e) {
            LoggerUtil.error(log, "replanTask error", e);
        }
    }

    /**
     * 根据现有的组和队列详情，重新规划任务
     *
     * @param detailList
     * @param preGroup
     * @return
     */
    public void generateRetryGroup(List<QueueDetailDTO> detailList, QueueGroupDTO preGroup) {
        try {
            LoggerUtil.info(log, "replanTask start instanceId:{},taskCode:{},queueGroupCode:{}", preGroup.getInstanceId(), preGroup.getTaskCode(), preGroup.getQueueGroupCode());
            Map<QueueStatus, List<QueueDetailDTO>> queueStatusMap = detailList.stream().collect(Collectors.groupingBy(QueueDetailDTO::getStatus));
            // 处理中的更新状态
            queueDetailService.updateProcessingQueueStatusByCallRecord(queueStatusMap.get(QueueStatus.PROCESSING));
            List<QueueDetailDTO> failedQueues = queueStatusMap.getOrDefault(QueueStatus.FAILED, new ArrayList<>());
            // 规划中的再次查询一次通话记录，根据callee 和instanceId 查询,通话记录数据 如果扩展参数里有当前的queuecode,taskCode，
            // 说明有呼叫记录,根据通话记录数据 更新状态，否则需要进行重新规划 后重试
            List<QueueDetailDTO> planningQueues = queueStatusMap.get(QueueStatus.PLANNING);
            if (CollectionUtils.isNotEmpty(planningQueues)) {
                failedQueues.addAll(queueDetailService.updatePlanningQueueAndFindRetryQueue(planningQueues));
            }
            if (CollectionUtils.isEmpty(failedQueues)) {
                LoggerUtil.info(log, "replanTask no failed queue,instanceId:{},taskCode:{},queueGroupCode:{}", preGroup.getInstanceId(), preGroup.getTaskCode(), preGroup.getQueueGroupCode());
                return;
            }
            LoggerUtil.info(log, "replanTaskFailedStart queue size:{},queueCodes:{},instanceId:{},taskCode:{}", failedQueues.size(), failedQueues.stream().map(QueueDetailDTO::getQueueCode).collect(Collectors.toList()), preGroup.getInstanceId(), preGroup.getTaskCode());
            failedQueues.forEach(queue -> {
                queue.setStatus(QueueStatus.PLANNING);
            });
            QueueGroupDTO groupDTO = QueueGroupDTO.builder().taskCode(preGroup.getTaskCode()).callTimeRange(preGroup.getCallTimeRange()).envId(preGroup.getEnvId()).groupStatus(GroupStatus.WAITING).instanceId(preGroup.getInstanceId()).queueCodes(failedQueues.stream().map(QueueDetailDTO::getQueueCode).collect(Collectors.toList())).groupType(preGroup.getGroupType()).taskCode(preGroup.getTaskCode()).extInfo(preGroup.getExtInfo()).queueGroupCode(preGroup.getQueueGroupCode()).build();
            queueDetailService.updateQueues(failedQueues);
            generateRetryGroup(groupDTO);
        } catch (Exception e) {
            LoggerUtil.error(log, "replanTaskError", e);
        }
    }

    /**
     * 将这个组重新进行规划
     *
     * @param preGroup
     */
    public void generateRetryGroup(QueueGroupDTO preGroup) {
        try {
            String preGroupCode = preGroup.getQueueGroupCode();
            // 检查是否需要停止当前组
            if (shouldStopCurrentGroup(preGroup)) {
                LoggerUtil.warn(log, "outCall not in call time range, stopQueueGroupCode:{},stop queueCode:{}, timeRange:{}", preGroupCode, preGroup.getQueueCodes(), CommonUtil.simplifyTimeRange(preGroup.getCallTimeRange()));
                if (preGroup.getGroupStatus() != GroupStatus.STOP) {
                    queueGroupService.stopQueueGroupAndQueue(preGroup);
                }
                return;
            }
            // 创建重试组
            QueueGroupDTO retryGroup = createRetryGroup(preGroup);
            queueGroupService.insertQueueGroup(Collections.singletonList(retryGroup));
            LoggerUtil.info(log, ",replanTask,instanceId:{},taskCode:{},queueGroupCode:{},queueSize:{},queueCodes:{}",
                    retryGroup.getInstanceId(), retryGroup.getTaskCode(),
                    retryGroup.getQueueGroupCode(), retryGroup.getQueueCodes().size(),
                    retryGroup.getQueueCodes());
        } catch (Exception e) {
            LoggerUtil.error(log, ",replanTaskError", e);
        }
    }

    /**
     * 判断是否需要停止当前组
     */
    private boolean shouldStopCurrentGroup(QueueGroupDTO preGroup) {
        Map<String, Object> extInfo = preGroup.getExtInfo();
        if (extInfo != null) {
            extInfo = new HashMap<>();
            preGroup.setExtInfo(extInfo);
        }
        // 重试组要看下重试次数
        int retryCount = getRetryCount(preGroup);
        if (retryCount >= outCallScheduleDrm.getMaxCallFlowRetries()) {
            preGroup.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.MAX_RETRIES);
            LoggerUtil.info(log, "current queueGroupCode:{},reach retryCount:{},", preGroup.getQueueGroupCode(), retryCount);
            return true;
        }
        CallTimeRange taskCallTimeRange = loadTaskCallTimeRange(preGroup.getInstanceId(), preGroup.getTaskCode());
        CallTimeRange.CallInfo callInfo = taskCallTimeRange.calCallTimeInfo();
        if (callInfo.isInCurrentCallTimeRange()) {
            return false;
        }
        boolean stop = !callInfo.isInTaskTimeRange();
        if (stop) {
            preGroup.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.NOT_MATCH_TIME);
        }
        return stop;
    }

    private int getRetryCount(QueueGroupDTO preGroup) {
        String queueGroupCode = preGroup.getQueueGroupCode();
        return CommonUtil.getGroupRetryCount(queueGroupCode);
    }

    /**
     * 检查任务是否在呼叫时间范围内
     */
    public boolean isInCallTimeRange(String instanceId, String taskCode) {
        CallTimeRange taskCallTimeRange = loadTaskCallTimeRange(instanceId, taskCode);
        return taskCallTimeRange.isInCallTimeRange();
    }

    /**
     * 创建重试组
     */
    private QueueGroupDTO createRetryGroup(QueueGroupDTO preGroup) {
        String newGroupCode = CommonUtil.buildRetryGroupCode(preGroup.getQueueGroupCode());
        return QueueGroupDTO.builder().groupType(QueueGroupType.RETRY).queueGroupCode(newGroupCode).taskCode(preGroup.getTaskCode()).queueCodes(preGroup.getQueueCodes()).instanceId(preGroup.getInstanceId()).envId(preGroup.getEnvId()).groupStatus(GroupStatus.WAITING).extInfo(new HashMap<>()).build();
    }

    /**
     * 根据任务计划
     *
     * @param taskDO
     */
    public void planTask(OutboundCallTaskDO taskDO) {
        LoggerUtil.info(log, "planTask, taskCode:{}, instanceId:{}", taskDO.getTaskCode(), taskDO.getInstanceId());
        String taskCode = taskDO.getTaskCode();
        String instanceId = taskDO.getInstanceId();
        String envFlag = taskDO.getEnvFlag();
        String lockKey = TaskPlanService.class.getSimpleName() + ":" + taskDO.getInstanceId() + ":" + taskCode + ":" + envFlag;
        boolean tryLock = redisLock.tryLock(lockKey, taskCode, 2 * 60);
        if (!tryLock) {
            LoggerUtil.info(log, "planTask tryLock fail, instanceId:{}, taskCode:{}, env:{}", instanceId, taskCode, envFlag);
            return;
        }
        try {
            if (TaskStatusEnum.allowCallUpgrade(taskDO.getTaskStatus())) {
                // 使用循环分页查询处理大数据量
                processLargeDataInBatches(taskDO, lockKey);
            } else {
                LoggerUtil.info(log, "planTask task status not allow call, instanceId:{}, taskCode:{}, env:{}", instanceId, taskCode, envFlag);
            }
        } catch (Exception e) {
            LoggerUtil.error(log, ",planTaskError, instanceId:{}, taskCode:{}, env:{}", instanceId, taskCode, envFlag, e);
        } finally {
            redisLock.unlock(lockKey, taskCode);
        }
    }

    private void processLargeDataInBatches(OutboundCallTaskDO taskDO, String lockKey) {
        int queryCount = 1;
        long totalGroupCount = 0;// 处理的分组总数
        String instanceId = taskDO.getInstanceId();
        String taskCode = taskDO.getTaskCode();
        CallTimeRange taskCallRange = loadTaskCallTimeRange(taskDO.getInstanceId(), taskDO.getTaskCode());
        CallTimeRange.CallInfo callInfo = taskCallRange.calCallTimeInfo();
        // 只要在任务时间范围内，队列分组的规划可以提前做
        if (!callInfo.isInTaskTimeRange()) {
            LoggerUtil.info(log, "planTask not in call time range," + " instanceId:{}, taskCode:{}, env:{}", taskDO.getInstanceId(), taskDO.getTaskCode(), taskDO.getEnvFlag());
            return;
        }
        while (true) {
            Boolean existKey = redisLock.existKey(lockKey);
            if (!existKey) {
                LoggerUtil.info(log, "not find lockKey:{}, break it", lockKey);
                break;
            }
            LoggerUtil.info(log, "processLargeDataInBatches start, taskCode:{}, instanceId:{}, env:{}, " + "callRange:{}", taskDO.getTaskCode(), taskDO.getInstanceId(), taskDO.getEnvFlag(), taskCallRange);
            // 分页查询待处理队列详情
            List<QueueDetailDTO> batchList = queryWaitingQueues(taskDO, 1, outCallScheduleDrm.getQueueQueryBatch());
            LoggerUtil.info(log, "processLargeDataInBatches queryWaitingQueues, taskCode:{}, instanceId:{}, env:{}, " + "batchListSize:{}", taskDO.getTaskCode(), taskDO.getInstanceId(), taskDO.getEnvFlag(), batchList.size());
            if (CollectionUtils.isEmpty(batchList)) {
                LoggerUtil.info(log, "processLargeDataInBatches no more data,instanceId:{}, taskCode:{}", taskDO.getInstanceId(), taskDO.getTaskCode());
                break;
            }
            long totalNormalGroupCount = 0;// 处理的普通分组数量
            long totalFixedTimeGroupCount = 0;// 处理的择时分组数量
            long totalRecordsCount = 0;// 处理的记录总数
            long startTime = System.currentTimeMillis();
            List<QueueGroupDTO> allQueueGroups = Collections.synchronizedList(new ArrayList<>());
            // 将批次数据分成1000一个子批次，每个线程处理一个子批次
            List<List<QueueDetailDTO>> subBatches = CommonUtil.partitionList(batchList, outCallScheduleDrm.getGroupingSize());
            LoggerUtil.info(log, "processLargeDataInBatches subBatches size:{},taskCode:{}", subBatches.size(), taskDO.getTaskCode());
            // 创建CompletableFuture列表来跟踪所有并行任务
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (List<QueueDetailDTO> subBatch : subBatches) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // 填充择时时间信息
                        populateFixedTime(subBatch, taskCallRange);
                        // 创建队列分组
                        List<QueueGroupDTO> queueGroups = createQueueGroups(subBatch);
                        allQueueGroups.addAll(queueGroups);
                        // 保存分组数据，注意这里大事务可能会超时
                        if (CollectionUtils.isNotEmpty(queueGroups)) {
                            transactionTemplate.execute((status) -> {
                                    queueDetailService.updateQueues(subBatch);
                                    queueGroupService.addQueueGroup2Max(queueGroups);
                                return null;
                            });
                        }
                    } catch (Exception e) {
                        LoggerUtil.error(log, "Error occurred in parallel task", e);
                        // 不抛出异常，确保其他任务可以继续执行
                    }
                });
                futures.add(future);
            }
            // 等待所有并行任务完成，无论成功还是失败
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            try {
                // 等待所有任务完成，设置超时时间避免无限等待
                allFutures.get(30, TimeUnit.MINUTES);
            } catch (Exception e) {
                LoggerUtil.error(log, "Error occurred during parallel processing or timeout", e);
                // 记录错误但继续处理已完成的任务
            }
            // 处理完成后的逻辑
            if (CollectionUtils.isNotEmpty(allQueueGroups)) {
                if (callInfo.isInCurrentCallTimeRange()) {
                    CompletableFuture.runAsync(new TracerRunnable() {
                        @Override
                        public void doRun() {
                            queueGroupService.startPlanningGroup(taskDO, false);
                        }
                    }, OutCallExecutorService.getQueueGroupthreadPool());
                }
                // 统计各类型分组数量
                long normalGroupCount = allQueueGroups.stream().filter(group -> group.getGroupType() == QueueGroupType.NORMAL).count();
                long fixedTimeGroupCount = allQueueGroups.stream().filter(group -> group.getGroupType() == QueueGroupType.FIXED_TIME).count();
                totalGroupCount += allQueueGroups.size();
                totalNormalGroupCount += normalGroupCount;
                totalFixedTimeGroupCount += fixedTimeGroupCount;
                long elapsedTime = System.currentTimeMillis() - startTime;
                LoggerUtil.info(log, "processLargeDataInBatches,instanceId:{}, taskCode:{} " + "totalGroups:{},currentGroups:{},normalGroups:{},fixedTimeGroups:{}" + ",:{}", instanceId, taskCode, totalGroupCount, allQueueGroups.size(), totalNormalGroupCount, totalFixedTimeGroupCount, elapsedTime);
            }
            queryCount++;
            // 数据库压力控制
            if (queryCount % 10 == 0) {
                LoggerUtil.info(log, "progress report: processed {} pages, {} total records, {} total groups, elapsed:{}ms", queryCount, totalRecordsCount, totalGroupCount, System.currentTimeMillis() - startTime);
                try {
                    Thread.sleep(500); // 减少压力，500ms休息
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LoggerUtil.error(log, "process interrupted by thread interruption", e);
                    break;
                }
            }
        }
    }

    private List<QueueDetailDTO> queryWaitingQueues(OutboundCallTaskDO taskDO, int pageNum, int pageSize) {
        QueueDetailRequest request = new QueueDetailRequest();
        request.setInstanceId(taskDO.getInstanceId());
        request.setTaskCode(taskDO.getTaskCode());
        request.setEnv(taskDO.getEnvFlag());
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        request.setStatus(QueueStatus.WAITING);
        // 只查询2天的新增数据
        request.setStartTime(new DateTime().withTimeAtStartOfDay().minusDays(1).toDate());
        request.setEndTime(new DateTime().withTimeAtStartOfDay().plusDays(1).toDate());
        PageData<List<QueueDetailDTO>> pageData = queueDetailService.queryPage(request);
        return pageData.getList() != null ? pageData.getList() : Collections.emptyList();
    }

    private void populateFixedTime(List<QueueDetailDTO> detailList, CallTimeRange taskCallRange) {
        if (CollectionUtils.isEmpty(detailList)) {
            return;
        }
        DateTime now = DateTime.now();
        // 获取时间映射
        Map<String, DateTime> phones2Time = getDateTimeMap(detailList);
        for (QueueDetailDTO queueDetailDto : detailList) {
            DateTime time = phones2Time.get(queueDetailDto.getCallee());
            if (time == null) {
                continue;
            }
            // 有可能新导入的数据对应的择时时间已经过了，这种情况不设置择时时间
            if (time.getHourOfDay() >= now.getHourOfDay() && taskCallRange.isInCallTimeRange(time)) {
                queueDetailDto.setFixedTime(CommonUtil.parse(time));
                queueDetailDto.setFixedStartTime(time.toDate());
            }
        }
    }

    private List<QueueGroupDTO> createQueueGroups(List<QueueDetailDTO> detailList) {
        if (CollectionUtils.isEmpty(detailList)) {
            return Collections.emptyList();
        }
        String taskCode = detailList.get(0).getTaskCode();
        LoggerUtil.info(log, "createQueueGroups start, taskCode:{}, totalRecords:{}", taskCode, detailList.size());
        // 按是否有固定时间分组
        Map<Boolean, List<QueueDetailDTO>> groupedDetails = detailList.stream().collect(Collectors.partitioningBy(detail -> detail.getFixedStartTime() != null));
        List<QueueDetailDTO> normalDetails = groupedDetails.get(false);
        List<QueueDetailDTO> fixedTimeDetails = groupedDetails.get(true);
        List<QueueGroupDTO> allGroups = new ArrayList<>();
        // 处理普通组
        if (CollectionUtils.isNotEmpty(normalDetails)) {
            List<QueueGroupDTO> normalGroups = createUnifiedGroups(normalDetails, QueueGroupType.NORMAL, null);
            allGroups.addAll(normalGroups);
            LoggerUtil.info(log, "created normal groups: {}, records: {}", normalGroups.size(), normalDetails.size());
        }
        // 按固定时间分组
        if (CollectionUtils.isNotEmpty(fixedTimeDetails)) {
            // 按固定时间分桶
            Map<Date, List<QueueDetailDTO>> timeBuckets = fixedTimeDetails.stream().collect(Collectors.groupingBy(QueueDetailDTO::getFixedStartTime));
            LoggerUtil.info(log, "processing fixed time buckets count: {}", timeBuckets.size());
            timeBuckets.forEach((timeKey, detailsForTime) -> {
                List<QueueGroupDTO> fixedGroups = createUnifiedGroups(detailsForTime, QueueGroupType.FIXED_TIME, timeKey);
                allGroups.addAll(fixedGroups);
                LoggerUtil.info(log, "created fixed time groups for {}: {}, records: {}", timeKey, fixedGroups.size(), detailsForTime.size());
            });
        }
        LoggerUtil.info(log, "createQueueGroups completed, total groups: {}, " + "total records: {}, group distribution: {}", allGroups.size(), detailList.size(), allGroups.stream().collect(Collectors.groupingBy(g -> g.getGroupType(), Collectors.counting())));
        return allGroups;
    }

    /**
     * 统一的组创建方法，处理普通组和固定时间组的创建逻辑
     */
    private List<QueueGroupDTO> createUnifiedGroups(List<QueueDetailDTO> details, QueueGroupType type, Date callStartTime) {
        String taskCode = details.get(0).getTaskCode();
        String instanceId = details.get(0).getInstanceId();
        int targetSize = outCallScheduleDrm.getGroupCodeNumLimitMin();
        List<QueueGroupDTO> groups = new ArrayList<>();
        List<List<QueueDetailDTO>> batches = CommonUtil.partitionList(details, targetSize);
        for (int i = 0; i < batches.size(); i++) {
            List<QueueDetailDTO> batch = batches.get(i);
            String groupCode = generateUnifiedGroupCode(type);
            // 更新队列状态
            for (QueueDetailDTO detail : batch) {
                detail.setGroupCode(groupCode);
                detail.setStatus(QueueStatus.PLANNING);
            }
            QueueGroupDTO group = QueueGroupDTO.builder().groupStatus(GroupStatus.WAITING).queueGroupCode(groupCode).queueCodes(batch.stream().map(QueueDetailDTO::getQueueCode).collect(Collectors.toList())).groupType(type).taskCode(taskCode).instanceId(instanceId).extInfo(new HashMap<>()).groupStartTime(callStartTime).build();
            LoggerUtil.info(log, "instanceId:{},taskCode:{},created group: {}, type: {}, size: {}", instanceId, taskCode, groupCode, type, batch.size());
            groups.add(group);
        }
        return groups;
    }

    private String generateUnifiedGroupCode(QueueGroupType type) {
        // 固定时间组
        if (type == QueueGroupType.FIXED_TIME) {
            return "F_" + UuidUtil.generateShortUuid();
        }
        return "N_" + UuidUtil.generateShortUuid();
    }

    /**
     * 加载任务的时间范围信息 todo @hanhan
     *
     * @param taskCode
     * @return
     */
    public CallTimeRange loadTaskCallTimeRange(String instanceId, String taskCode) {
        // 构建缓存key
        String cacheKey = buildTaskCallTimeRangeCacheKey(instanceId, taskCode);
        // 尝试从缓存中获取
        try {
            String cachedValue = getFromCache(cacheKey);
            if (StringUtils.isNotBlank(cachedValue)) {
                CallTimeRange cachedRange = deserializeCallTimeRange(cachedValue);
                if (cachedRange != null) {
                    log.debug("Hit cache for task call time range, instanceId:{}, taskCode:{}", instanceId, taskCode);
                    return cachedRange;
                }
            }
            // 从数据库加载
            OutboundCallTaskDO outboundCallTaskDO = outboundCallTaskService.queryOneCallTaskByCode(instanceId, taskCode);
            if (outboundCallTaskDO == null || StringUtils.isBlank(outboundCallTaskDO.getTaskRulesCode())) {
                LoggerUtil.warn(log, "Task not found or taskRulesCode is empty, instanceId:{}, taskCode:{}", instanceId, taskCode);
                CallTimeRange callTimeRange = new CallTimeRange();
                return callTimeRange;
            }
            return cacheCallTimeRange(instanceId, taskCode, outboundCallTaskDO.getTaskRulesCode());
        } catch (Exception e) {
            LoggerUtil.warn(log, "Failed to get task call time range from cache, instanceId:{}, taskCode:{}", instanceId, taskCode, e);
        }
        return new CallTimeRange();
    }

    /**
     * 从缓存获取值（支持本地/Redis双模式）
     */
    private String getFromCache(String key) {
        if (useLocalCache()) {
            return localCache.get(key);
        }
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 写入缓存（支持本地/Redis双模式）
     */
    private void putToCache(String key, String value, long expireSeconds) {
        if (useLocalCache()) {
            localCache.put(key, value);
            // 本地模式不支持过期，但可以通过额外map实现，这里简化处理
            return;
        }
        redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 删除缓存（支持本地/Redis双模式）
     */
    private void deleteFromCache(String key) {
        if (useLocalCache()) {
            localCache.remove(key);
            return;
        }
        redisTemplate.delete(key);
    }

    /**
     * 删除缓存的时间范围信息
     */
    public void deleteTaskCallTimeRange(String instanceId, String taskCode) {
        // 构建缓存key
        String cacheKey = buildTaskCallTimeRangeCacheKey(instanceId, taskCode);
        // 删除缓存
        try {
            deleteFromCache(cacheKey);
            LoggerUtil.info(log, "Deleted task call time range from cache, instanceId:{}, taskCode:{}", instanceId, taskCode);
        } catch (Exception e) {
            LoggerUtil.warn(log, "Failed to delete task call time range from cache, instanceId:{}, taskCode:{}", instanceId, taskCode, e);
        }
    }

    /**
     * 缓存任务的时间范围信息
     */
    public CallTimeRange cacheCallTimeRange(String instanceId, String taskCode, String taskRulesCode) {
        CallTimeRange callTimeRange = new CallTimeRange();
        OutboundCallTaskRulesDO taskRulesDO = outboundCallTaskRulesService.getByInstanceAndCode(instanceId, taskRulesCode);
        LoggerUtil.info(log, "cacheCallTimeRange, instanceId:{}, taskCode:{} taskRulesCode: {} taskRulesDO: {}", instanceId, taskCode, taskRulesCode, taskRulesDO);
        // 空值检查
        if (taskRulesDO == null) {
            LoggerUtil.warn(log, "TaskRulesDO not found, instanceId:{}, taskCode:{}, taskRulesCode:{}", instanceId, taskCode, taskRulesCode);
            return callTimeRange;
        }
        List<CallTimeRange.CallTime> callTimes = JSON.parseArray(taskRulesDO.getTaskRulesDetail(), CallTimeRange.CallTime.class);
        callTimeRange.setCallTimes(callTimes);
        callTimeRange.setTaskStartTime(taskRulesDO.getTakeEffectTime());
        callTimeRange.setTaskEndTime(taskRulesDO.getInvalidTime());
        callTimeRange.setTaskCode(taskCode);
        // 将结果存入缓存
        try {
            String cacheKey = buildTaskCallTimeRangeCacheKey(instanceId, taskCode);
            String serializedValue = serializeCallTimeRange(callTimeRange);
            if (serializedValue != null) {
                putToCache(cacheKey, serializedValue, TimeUnit.DAYS.toSeconds(1));
                LoggerUtil.info(log, "Cached task call time range, instanceId:{}, taskCode:{} taskRulesCode: {} callTimeRange: {}", instanceId, taskCode, taskRulesCode, callTimeRange);
            }
        } catch (Exception e) {
            LoggerUtil.warn(log, "Failed to cache task call time range, instanceId:{}, taskCode:{}", instanceId, taskCode, e);
        }
        return callTimeRange;
    }

    /**
     * 将Integer类型的时间(如900表示09:00)解析为今天的DateTime对象
     *
     * @param time 时间整数，如900表示09:00
     * @return 今天的DateTime对象
     */
    private DateTime parseTime(Integer time) {
        if (time == null) {
            return new DateTime().withTimeAtStartOfDay();
        }
        // 将整数转换为小时和分钟
        int hour = time / 100;
        int minute = time % 100;
        // 返回今天的指定时间
        return new DateTime().withTimeAtStartOfDay().withHourOfDay(hour).withMinuteOfHour(minute);
    }

    private Map<String, DateTime> getDateTimeMap(List<QueueDetailDTO> detailList) {
        if (CollectionUtils.isEmpty(detailList)) {
            return Collections.emptyMap();
        }
        List<QueueDetailDTO> fixTimeList = new ArrayList<>();
        for (QueueDetailDTO queueDetailDto : detailList) {
            Map<String, Object> extInfo = queueDetailDto.getExtInfo();
            if (extInfo == null || null == extInfo.get(CommonConstants.TIMING_FLAG) || extInfo.get(CommonConstants.TIMING_FLAG).equals("false")) {
                continue;
            }
            fixTimeList.add(queueDetailDto);
        }
        log.info("getDateTimeMap fixTimeList:{}", JSON.toJSONString(fixTimeList.size()));
        if (CollectionUtils.isEmpty(fixTimeList)) {
            return Collections.emptyMap();
        }
        List<String> calleeList = fixTimeList.stream().map(QueueDetailDTO::getCallee).filter(Objects::nonNull).collect(Collectors.toList());
        if (calleeList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, DateTime> phones2Time = new HashMap<>(calleeList.size());
        int batchSize = ScheduleConstants.TIMING_INFO_QUERY_MAX;
        LoggerUtil.info(log, "query timing info for phones count:{}, batches: {}", calleeList.size(), (calleeList.size() + batchSize - 1) / batchSize);
        CommonUtil.partitionList(calleeList, batchSize).forEach(batch -> {
            try {
                List<OutboundTimingInfoDO> timingInfoList = timingInfoService.listOutboundTimingInfoDO(batch);
                if (CollectionUtils.isNotEmpty(timingInfoList)) {
                    timingInfoList.forEach(timingInfo -> {
                        DateTime dateTime = CommonUtil.convertToTodayTime(timingInfo.getTiming());
                        phones2Time.put(timingInfo.getPhone(), dateTime);
                    });
                }
            } catch (Exception e) {
                LoggerUtil.error(log, "failed to query timing info for batch phones: {}", batch.size(), e);
            }
        });
        LoggerUtil.info(log, "timing info mapping completed, phones mapped: {}/{} ({}%)", phones2Time.size(), calleeList.size(), String.format("%.1f", phones2Time.size() * 100.0 / calleeList.size()));
        return phones2Time;
    }
}