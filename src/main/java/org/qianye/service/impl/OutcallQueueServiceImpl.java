package org.qianye.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.qianye.DTO.*;
import org.qianye.cache.CacheClient;
import org.qianye.common.OutCallResult;
import org.qianye.common.PageData;
import org.qianye.common.QueueStatus;
import org.qianye.engine.OutCallExecutorService;
import org.qianye.engine.OutCallSlotServiceImpl;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.entity.OutcallQueueDO;
import org.qianye.mapper.OutcallQueueMapper;
import org.qianye.service.OutboundCallTaskService;
import org.qianye.service.OutcallQueueService;
import org.qianye.util.CommonUtil;
import org.qianye.util.LoggerUtil;
import org.qianye.util.OutPlanUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OutcallQueueServiceImpl extends ServiceImpl<OutcallQueueMapper, OutcallQueueDO>
        implements OutcallQueueService {
    private static final String SLOT_RELEASED = "slotReleased";

    @Value("${env}")
    private String env;
    @Value("${outcall.queue.import.batch-size:200}")
    private int queueImportBatchSize;
    @Value("${outcall.queue.import.max-parallelism:4}")
    private int queueImportMaxParallelism;
    @Resource
    private CallRecordService callRecordService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private OutboundCallTaskService outboundCallTaskService;
    @Resource
    private OutPlanUtil outPlanComHelper;
    @Resource
    private OutCallSlotServiceImpl outCallSlotService;

    @Override
    public QueueDetailDTO getByInstanceAndCode(String instanceId, String queueCode, String envId) {
        OutcallQueueDO one = getOne(new LambdaQueryWrapper<OutcallQueueDO>()
                .eq(OutcallQueueDO::getInstanceId, instanceId)
                .eq(OutcallQueueDO::getQueueCode, queueCode)
                .eq(OutcallQueueDO::getEnvId, envId));
        return convertToDTO(one);
    }

    @Override
    public boolean updateStatus(String instanceId, String queueCode, String envId, String status) {
        return update(new LambdaUpdateWrapper<OutcallQueueDO>()
                .eq(OutcallQueueDO::getInstanceId, instanceId)
                .eq(OutcallQueueDO::getQueueCode, queueCode)
                .eq(OutcallQueueDO::getEnvId, envId)
                .set(OutcallQueueDO::getQueueStatus, status));
    }

    @Override
    public void removeById(Long id) {
        baseMapper.deleteById(id);
    }

    @Override
    public void checkQueueStatus() {
        boolean lock = cacheClient.putNotExist("checkQueueDetail:" + env, "1", 60 * 4);
        if (!lock) {
            LoggerUtil.info(log, "checkQueueDetail lock fail, ignore it");
            return;
        }
        LoggerUtil.info(log, "checkQueueStaus start");
        // 分页处理所有进行中的任务
        int pageNum = 1;
        int pageSize = 200;
        PageDTO<OutboundCallTaskDO> processingTaskPage;
        do {
            processingTaskPage = outboundCallTaskService.queryProcessingTaskList(pageNum, pageSize);
            if (CollectionUtils.isEmpty(processingTaskPage.getRecords())) {
                LoggerUtil.info(log, "checkQueueDetail no processing task on page {}, ignore it", pageNum);
                break;
            }
            for (OutboundCallTaskDO task : processingTaskPage.getRecords()) {
                CompletableFuture.runAsync(() -> checkProcessingQueues(task));
            }
            // 处理下一页
            pageNum++;
        } while (processingTaskPage.getRecords().size() == pageSize);
    }

    private void checkProcessingQueues(OutboundCallTaskDO task) {
        QueueDetailRequest request = new QueueDetailRequest();
        request.setInstanceId(task.getInstanceId());
        request.setTaskCode(task.getTaskCode());
        request.setEnv(task.getEnvFlag());
        request.setPageNum(1);
        request.setPageSize(2000);
        request.setStatus(QueueStatus.PROCESSING);
        DateTime dateTime = new DateTime();
        // 60分钟前到5小时前的数据
        request.setEndTime(dateTime.minusMinutes(60).toDate());
        request.setStartTime(dateTime.minusHours(5).toDate());        // 循环处理直到没有处理中的任务
        PageData<List<QueueDetailDTO>> pageData;
        do {
            pageData = queryPage(request);
            List<QueueDetailDTO> processingQueues = pageData.getList().stream()
                    .filter(queue -> StringUtils.isNotEmpty(queue.getAcid()))
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(processingQueues)) {
                updateProcessingQueueStatusByCallRecord(processingQueues);
            }
        } while (pageData.getList().size() == request.getPageSize()); // 当返回记录数小于pageSize时，说明已经处理完所有数据
    }

    @Override
    public void updateProcessingQueueStatusByCallRecord(List<QueueDetailDTO> processingQueues) {
        if (CollectionUtils.isEmpty(processingQueues)) {
            return;
        }
        for (QueueDetailDTO queue : processingQueues) {
            log.info("queueProcessStatusByCallRecord queue:{}", JSONObject.toJSONString(queue));
            QueryCallRecordRequest queryCallRecordRequest = new QueryCallRecordRequest();
            queryCallRecordRequest.setInstanceId(queue.getInstanceId());
            if (StringUtils.isNotEmpty(queue.getAcid())) {
                queryCallRecordRequest.setAcid(queue.getAcid());
            } else if (StringUtils.isNotEmpty(queue.getCallee())) {
                queryCallRecordRequest.setCallee(queue.getCallee());
            }
            QueryCallRecordResponse response = callRecordService.queryCallRecordList(queryCallRecordRequest);
            if (response == null || CollectionUtils.isEmpty(response.getList())) {
                outPlanComHelper.setQueueToStop(queue, OutCallResult.CALL_NOT_FOUND);
                continue;
            }
            CallRecord callRecord = response.getList().get(0);
            boolean isSuccess = outPlanComHelper.isCallSuccessful(callRecord);
            if (isSuccess) {
                outPlanComHelper.setQueueToSuccess(queue);
            } else {
                outPlanComHelper.setQueueToStop(queue, OutCallResult.CALL_FAIL);
            }
            releaseSlotIfNeeded(queue);
            LoggerUtil.info(log, "updateProcessingQueueStatusByCallRecord,instanceId:{},taskCode:{}," +
                            "processingQueue:{},updateStatus:{}", queue.getInstanceId(), queue.getTaskCode(), queue.getQueueCode(),
                    queue.getStatus());
        }
        updateQueues(processingQueues);
    }

    @Override
    public List<QueueDetailDTO> updatePlanningQueueAndFindRetryQueue(List<QueueDetailDTO> planningQueues) {
        if (CollectionUtils.isEmpty(planningQueues)) {
            log.info("updatePlanningQueueAndFindRetryQueuePlanningQueues is empty");
            return new ArrayList<>(); // 返回空列表而不是null
        }
        // 返回需要重试的队列
        List<QueueDetailDTO> resultList = new ArrayList<>();
        // 更新的队列
        List<QueueDetailDTO> updateList = new ArrayList<>();        // 1、查询通话记录
        for (QueueDetailDTO queue : planningQueues) {
            log.info("planningQueueAndFindRetryQueue queue:{}", JSONObject.toJSONString(queue));
            QueryCallRecordRequest queryCallRecordRequest = new QueryCallRecordRequest();
            queryCallRecordRequest.setInstanceId(queue.getInstanceId());
            if (StringUtils.isNotEmpty(queue.getAcid())) {
                queryCallRecordRequest.setAcid(queue.getAcid());
            } else if (StringUtils.isNotEmpty(queue.getCallee())) {
                queryCallRecordRequest.setCallee(queue.getCallee());
            }
            QueryCallRecordResponse response = callRecordService.queryCallRecordList(queryCallRecordRequest);
            log.info("updatePlanningQueueAndFindRetryQueue response:{},request:{}", JSONObject.toJSONString(response), JSONObject.toJSONString(queue));
            if (response == null || CollectionUtils.isEmpty(response.getList())) {
                resultList.add(queue);
                continue;
            }
            boolean foundMatch = false;
            for (CallRecord callRecord : response.getList()) {
                String taskRecordCode =callRecord.getTaskCode();
                if (StringUtils.isBlank(taskRecordCode)) {
                    continue;
                }
                if (queue.getQueueCode().equals(taskRecordCode)) {
                    queue.setAcid(callRecord.getAcid());
                    queue.setCallee(callRecord.getCallee());
                    queue.setCaller(callRecord.getCaller());
                    queue.setCallStartTime(callRecord.getStartTime());
                    queue.setCallEndTime(callRecord.getReleaseTime());
                    updateList.add(queue);
                    foundMatch = true;
                    boolean isSuccess = outPlanComHelper.isCallSuccessful(callRecord);
                    if (isSuccess) {
                        outPlanComHelper.setQueueToSuccess(queue);
                    } else {
                        outPlanComHelper.setQueueToStop(queue, OutCallResult.CALL_FAIL);
                    }
                    break;
                }
            }
            // 如果没有找到匹配的taskRecordCode，添加到resultList中
            if (!foundMatch) {
                resultList.add(queue);
            }
        }
        // 2、根据通话记录更新状态
        if (!CollectionUtils.isEmpty(updateList)) {
            updateQueues(updateList);
        }
        // 3、返回需要重试的队列
        return resultList;
    }

    @Override
    public List<QueueDetailDTO> queueDetailDTOList(QueueDetailRequest request) {
        LoggerUtil.info(log, "queueDetailDTOListRequest instanceId:{},taskCode:{},calleeListSize:{}", request.getInstanceId(), request.getTaskCode(), request.getCalleeList().size());
        LambdaQueryWrapper<OutcallQueueDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueDO::getInstanceId, request.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueDO::getTaskCode, request.getTaskCode());
        lambdaQueryWrapper.eq(OutcallQueueDO::getEnvId, env);
        if (request.getStartTime() != null) {
            lambdaQueryWrapper.ge(OutcallQueueDO::getGmtCreate, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            lambdaQueryWrapper.le(OutcallQueueDO::getGmtCreate, request.getEndTime());
        }
        if (!CollectionUtils.isEmpty(request.getCalleeList())) {
            if (request.getCalleeList().size() > 500) {
                throw new RuntimeException("calleeList size is too large:{}" + request.getInstanceId() + "_" + request.getTaskCode());
            }
            lambdaQueryWrapper.in(OutcallQueueDO::getCallee, request.getCalleeList());
        }
        List<OutcallQueueDO> detailDOList = baseMapper.selectList(lambdaQueryWrapper);
        if (!CollectionUtils.isEmpty(detailDOList)) {
            return detailDOList.stream().map(this::convertToDTO).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    public PageData<List<QueueDetailDTO>> queryPage(QueueDetailRequest request) {
        if (StringUtils.isEmpty(request.getTaskCode())) {
            throw new RuntimeException("taskCode can not be empty");
        }
        LoggerUtil.info(log, "queryQueueRequest request:{}", JSONObject.toJSONString(request));
        LambdaQueryWrapper<OutcallQueueDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueDO::getInstanceId, request.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueDO::getTaskCode, request.getTaskCode());
        if (request.getStartTime() != null) {
            lambdaQueryWrapper.ge(OutcallQueueDO::getGmtModified, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            lambdaQueryWrapper.le(OutcallQueueDO::getGmtModified, request.getEndTime());
        }
        if (request.getStatus() != null) {
            lambdaQueryWrapper.eq(OutcallQueueDO::getQueueStatus, request.getStatus().name());
        } else if (!CollectionUtils.isEmpty(request.getQueueStatusList())) {
            lambdaQueryWrapper.in(OutcallQueueDO::getQueueStatus, request.getQueueStatusList());
        }
        lambdaQueryWrapper.eq(OutcallQueueDO::getEnvId, env);
        lambdaQueryWrapper.orderByDesc(OutcallQueueDO::getGmtModified);
        PageDTO<OutcallQueueDO> pageQuery = new PageDTO<>(request.getPageNum(), request.getPageSize());
        PageDTO<OutcallQueueDO> pageDTO = baseMapper.selectPage(pageQuery, lambdaQueryWrapper);
        List<QueueDetailDTO> queueDetailDTOList = new ArrayList<>();
        if (pageDTO.getRecords() != null) {
            for (OutcallQueueDO queueDetailDO : pageDTO.getRecords()) {
                QueueDetailDTO queueDetailDTO = new QueueDetailDTO();
                queueDetailDTO.setInstanceId(queueDetailDO.getInstanceId());
                queueDetailDTO.setTaskCode(queueDetailDO.getTaskCode());
                queueDetailDTO.setQueueCode(queueDetailDO.getQueueCode());
                queueDetailDTO.setGroupCode(queueDetailDO.getGroupCode());
                queueDetailDTO.setStatus(QueueStatus.valueOf(queueDetailDO.getQueueStatus().toUpperCase()));
                queueDetailDTO.setCallee(queueDetailDO.getCallee());
                queueDetailDTO.setCaller(queueDetailDO.getCaller());
                queueDetailDTO.setAcid(queueDetailDO.getAcid());
                queueDetailDTO.setCallStartTime(queueDetailDO.getCallStartTime());
                queueDetailDTO.setCallEndTime(queueDetailDO.getCallEndTime());
                if (queueDetailDO.getCallCount() != null) {
                    queueDetailDTO.setCallCount(queueDetailDO.getCallCount());
                } else {
                    queueDetailDTO.setCallCount(0);
                }
                queueDetailDTO.setEnvId(queueDetailDO.getEnvId());
                queueDetailDTO.setGmtCreate(queueDetailDO.getGmtCreate());
                queueDetailDTO.setGmtModified(queueDetailDO.getGmtModified());
                Map<String, Object> extInfo = JSONObject.parseObject(queueDetailDO.getExtInfo(), Map.class);
                if (extInfo == null) {
                    extInfo = new HashMap<>();
                }
                queueDetailDTO.setExtInfo(extInfo);
                queueDetailDTOList.add(queueDetailDTO);
            }
            return new PageData<>(queueDetailDTOList, pageDTO.getCurrent(), pageDTO.getSize(), pageDTO.getTotal());
        }
        return new PageData<>(Collections.emptyList(), pageDTO.getCurrent(), pageDTO.getSize(), pageDTO.getTotal());
    }

    @Override
    public void updateQueues(List<QueueDetailDTO> queueDetailDTOList) {
        for (QueueDetailDTO dto : queueDetailDTOList) {
            update(new LambdaUpdateWrapper<OutcallQueueDO>()
                    .eq(OutcallQueueDO::getInstanceId, dto.getInstanceId())
                    .eq(OutcallQueueDO::getQueueCode, dto.getQueueCode())
                    .eq(OutcallQueueDO::getEnvId, env)
                    .set(OutcallQueueDO::getQueueStatus, dto.getStatus().name())
                    .set(OutcallQueueDO::getGroupCode, dto.getGroupCode())
                    .set(OutcallQueueDO::getCallCount, dto.getCallCount())
                    .set(OutcallQueueDO::getCallStartTime, dto.getCallStartTime())
                    .set(OutcallQueueDO::getCallEndTime, dto.getCallEndTime())
                    .set(OutcallQueueDO::getExtInfo, JSONObject.toJSONString(dto.getExtInfo()))
                    .set(OutcallQueueDO::getGmtModified, new Date()));
        }
    }

    private List<OutcallQueueDO> convertToDOList(List<QueueDetailDTO> batchDTOs) {
        List<OutcallQueueDO> queueDetailDOList = new ArrayList<>(batchDTOs.size());
        Date now = new Date();
        for (QueueDetailDTO dto : batchDTOs) {
            OutcallQueueDO entity = new OutcallQueueDO();
            entity.setInstanceId(dto.getInstanceId());
            entity.setQueueCode(dto.getQueueCode());
            entity.setTaskCode(dto.getTaskCode());
            entity.setGroupCode(dto.getGroupCode());
            entity.setCaller(dto.getCaller());
            entity.setCallee(dto.getCallee());
            entity.setAcid(dto.getAcid());
            entity.setCallCount(dto.getCallCount());
            if (dto.getCallStartTime() != null) {
                entity.setCallStartTime(dto.getCallStartTime());
            }
            if (dto.getCallEndTime() != null) {
                entity.setCallEndTime(dto.getCallEndTime());
            }
            entity.setEnvId(env);
            entity.setGmtCreate(now);
            entity.setGmtModified(now);
            entity.setQueueStatus(dto.getStatus().name());
            entity.setExtInfo(JSONObject.toJSONString(dto.getExtInfo()));
            queueDetailDOList.add(entity);
        }
        return queueDetailDOList;
    }

    /**
     * 根据最后状态更新队列详情
     *
     * @param queue
     */
    @Override
    public void updateByLastStatus(QueueDetailDTO queue) {
        OutcallQueueDO queueDetailDO = JSONObject.parseObject(JSONObject.toJSONString(queue), new TypeReference<OutcallQueueDO>() {
        });
        queueDetailDO.setGmtModified(new Date());
        queueDetailDO.setQueueStatus(queue.getStatus().name());
        queueDetailDO.setExtInfo(JSONObject.toJSONString(queue.getExtInfo()));
        LambdaQueryWrapper<OutcallQueueDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueDO::getInstanceId, queueDetailDO.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueDO::getTaskCode, queueDetailDO.getTaskCode());
        lambdaQueryWrapper.eq(OutcallQueueDO::getEnvId, env);
        lambdaQueryWrapper.eq(OutcallQueueDO::getQueueStatus, queue.getLastQueueStatus());
        baseMapper.update(queueDetailDO, lambdaQueryWrapper);
    }

    @Override
    public void updateByCode(QueueDetailDTO queueDetailDTO) {
        OutcallQueueDO queueDetailDO = JSONObject.parseObject(JSONObject.toJSONString(queueDetailDTO), new TypeReference<OutcallQueueDO>() {
        });
        queueDetailDO.setGmtModified(new Date());
        queueDetailDO.setQueueStatus(queueDetailDTO.getStatus().name());
        queueDetailDO.setExtInfo(JSONObject.toJSONString(queueDetailDTO.getExtInfo()));
        LambdaQueryWrapper<OutcallQueueDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueDO::getInstanceId, queueDetailDO.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueDO::getTaskCode, queueDetailDO.getTaskCode());
        lambdaQueryWrapper.eq(OutcallQueueDO::getQueueCode, queueDetailDO.getQueueCode());
        lambdaQueryWrapper.eq(OutcallQueueDO::getEnvId, env);
        int result = baseMapper.update(queueDetailDO, lambdaQueryWrapper);
        LoggerUtil.info(log, "updateByCode，result:{}, queueDetailDTO:{}", result, JSONObject.toJSONString(queueDetailDTO));
    }

    @Override
    public QueueDetailDTO getByCode(String instanceId, String queueCode) {
        LoggerUtil.info(log, "getByCode instanceId:{}, queueCode:{}", instanceId, queueCode);
        LambdaQueryWrapper<OutcallQueueDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueDO::getInstanceId, instanceId);
        lambdaQueryWrapper.eq(OutcallQueueDO::getQueueCode, queueCode);
        lambdaQueryWrapper.eq(OutcallQueueDO::getEnvId, env);
        OutcallQueueDO queueDetailDO = baseMapper.selectOne(lambdaQueryWrapper);
        if (queueDetailDO != null) {
            return convertToDTO(queueDetailDO);
        }
        return null;
    }

    @Override
    public QueueDetailDTO getOneByDetail(QueueDetailDTO queueDetailDto) {
        LoggerUtil.info(log, "getByDetail instanceId:{}, taskCode:{}", JSONObject.toJSONString(queueDetailDto));
        LambdaQueryWrapper<OutcallQueueDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OutcallQueueDO::getInstanceId, queueDetailDto.getInstanceId());
        lambdaQueryWrapper.eq(OutcallQueueDO::getTaskCode, queueDetailDto.getTaskCode());
        lambdaQueryWrapper.eq(OutcallQueueDO::getEnvId, env);
        if (StringUtils.isNotEmpty(queueDetailDto.getQueueCode())) {
            lambdaQueryWrapper.eq(OutcallQueueDO::getQueueCode, queueDetailDto.getQueueCode());
        }
        if (StringUtils.isNotEmpty(queueDetailDto.getCallee())) {
            lambdaQueryWrapper.eq(OutcallQueueDO::getCallee, queueDetailDto.getCallee());
        }
        lambdaQueryWrapper.orderByDesc(OutcallQueueDO::getGmtCreate);
        List<OutcallQueueDO> queueDetailDOS = baseMapper.selectList(lambdaQueryWrapper);
        if (!CollectionUtils.isEmpty(queueDetailDOS)) {
            return convertToDTO(queueDetailDOS.get(0));
        }
        return null;
    }

    @Override
    public List<QueueDetailDTO> getByCodes(String instanceId, List<String> queueCodes) {
        LoggerUtil.info(log, "query queue by code,instanceId:{},queueCode size:{}", instanceId, queueCodes.size());
        long currentTimeMillis = System.currentTimeMillis();
        List<QueueDetailDTO> result = new ArrayList<>();
        // 每批次处理200条，避免SQL IN子句过长
        int batchSize = ScheduleConstants.BATCH_QUERY_LIMIT;
        for (int i = 0; i < queueCodes.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, queueCodes.size());
            List<String> batchCodes = queueCodes.subList(i, endIndex);
            LambdaQueryWrapper<OutcallQueueDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(OutcallQueueDO::getInstanceId, instanceId);
            lambdaQueryWrapper.in(OutcallQueueDO::getQueueCode, batchCodes);
            lambdaQueryWrapper.eq(OutcallQueueDO::getEnvId, env);
            // 使用普通列表查询替代分页查询，更高效
            List<OutcallQueueDO> queueDetailDOS = baseMapper.selectList(lambdaQueryWrapper);
            if (queueDetailDOS != null && !queueDetailDOS.isEmpty()) {
                // 使用高效的对象映射方式替代JSON序列化
                for (OutcallQueueDO queueDetailDO : queueDetailDOS) {
                    result.add(convertToDTO(queueDetailDO));
                }
            }
        }
        LoggerUtil.info(log, "query queue by code,cost time:{}", System.currentTimeMillis() - currentTimeMillis);
        return result;
    }

    /**
     * 高效的DO到DTO转换方法
     */
    private QueueDetailDTO convertToDTO(OutcallQueueDO queueDetailDO) {
        if (queueDetailDO == null) {
            return null;
        }
        QueueDetailDTO dto = new QueueDetailDTO();
        dto.setInstanceId(queueDetailDO.getInstanceId());
        dto.setQueueCode(queueDetailDO.getQueueCode());
        dto.setTaskCode(queueDetailDO.getTaskCode());
        dto.setCaller(queueDetailDO.getCaller());
        dto.setCallee(queueDetailDO.getCallee());
        dto.setAcid(queueDetailDO.getAcid());
        dto.setEnvId(queueDetailDO.getEnvId());
        dto.setGmtCreate(queueDetailDO.getGmtCreate());
        dto.setGmtModified(queueDetailDO.getGmtModified());
        dto.setGroupCode(queueDetailDO.getGroupCode());
        if (queueDetailDO.getCallCount() != null) {
            dto.setCallCount(queueDetailDO.getCallCount());
        } else {
            dto.setCallCount(0);
        }
        // 状态转换：String -> QueueStatus（支持大小写不敏感）
        if (queueDetailDO.getQueueStatus() != null) {
            try {
                dto.setStatus(QueueStatus.valueOf(queueDetailDO.getQueueStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                LoggerUtil.warn(log, "Invalid queue status: {}", queueDetailDO.getQueueStatus());
            }
        }
        // 时间转换：Date -> DateTime
        if (queueDetailDO.getCallStartTime() != null) {
            dto.setCallStartTime(queueDetailDO.getCallStartTime());
        }
        if (queueDetailDO.getCallEndTime() != null) {
            dto.setCallEndTime(queueDetailDO.getCallEndTime());
        }
        // 扩展信息转换：String -> Map
        if (StringUtils.isNotBlank(queueDetailDO.getExtInfo())) {
            try {
                Map<String, Object> extMap = JSONObject.parseObject(queueDetailDO.getExtInfo(), Map.class);
                if (extMap != null) {
                    dto.setExtInfo(extMap);
                }
            } catch (Exception e) {
                LoggerUtil.warn(log, "Failed to parse extInfo: {}", queueDetailDO.getExtInfo(), e);
            }
        }
        return dto;
    }

    private void releaseSlotIfNeeded(QueueDetailDTO queue) {
        if (queue == null) {
            return;
        }
        if (Boolean.TRUE.equals(queue.getExtInfo().get(SLOT_RELEASED))) {
            return;
        }
        outCallSlotService.releaseSlots(queue);
        queue.getExtInfo().put(SLOT_RELEASED, true);
    }

    @Override
    public void batchInsertQueue(List<QueueDetailDTO> queueDetailDTOList) {
        if (CollectionUtils.isEmpty(queueDetailDTOList)) {
            LoggerUtil.warn(log, "batchInsertQueue ignored empty request");
            return;
        }
        LoggerUtil.info(log, "batchInsertQueueDTOList:{},{},{}", queueDetailDTOList.size(),
                queueDetailDTOList.get(0).getInstanceId(), queueDetailDTOList.get(0).getTaskCode(),
                JSONObject.toJSONString(queueDetailDTOList.get(0).getExtInfo()));
        long start = System.currentTimeMillis();
        List<OutcallQueueDO> queueDOList = convertToDOList(queueDetailDTOList);
        int batchSize = Math.max(1, queueImportBatchSize);
        List<List<OutcallQueueDO>> partitions = CommonUtil.partitionList(queueDOList, batchSize);
        int parallelism = Math.max(1, Math.min(queueImportMaxParallelism, partitions.size()));
        try {
            for (int i = 0; i < partitions.size(); i += parallelism) {
                List<List<OutcallQueueDO>> window = partitions.subList(i, Math.min(i + parallelism, partitions.size()));
                CompletableFuture<?>[] futures = window.stream()
                        .map(batch -> CompletableFuture.runAsync(() -> {
                            int inserted = baseMapper.batchInsert(batch);
                            if (inserted != batch.size()) {
                                LoggerUtil.warn(log, "batchInsertQueue inserted rows mismatch, expect:{}, actual:{}, taskCode:{}",
                                        batch.size(), inserted, batch.get(0).getTaskCode());
                            }
                        }, OutCallExecutorService.getImportQueueThreadPool()))
                        .toArray(CompletableFuture[]::new);
                CompletableFuture.allOf(futures).join();
            }
        } catch (Exception e) {
            LoggerUtil.error(log, "batchInsertQueue failed, taskCode:{}, totalSize:{}, batchSize:{}, parallelism:{}",
                    queueDetailDTOList.get(0).getTaskCode(), queueDetailDTOList.size(), batchSize, parallelism, e);
            throw new RuntimeException("batch insert queue failed", e);
        }
        LoggerUtil.info(log, "batchInsertQueue completed, taskCode:{}, totalSize:{}, batchCount:{}, parallelism:{}, cost:{}ms",
                queueDetailDTOList.get(0).getTaskCode(), queueDetailDTOList.size(), partitions.size(),
                parallelism, System.currentTimeMillis() - start);
    }

    @Override
    public List<QueueDetailDTO> getByTaskCode(String instanceId, String taskCode, String envId) {
        LoggerUtil.info(log, "getByTaskCode instanceId:{}, taskCode:{}, envId:{}", instanceId, taskCode, envId);
        List<OutcallQueueDO> list = list(new LambdaQueryWrapper<OutcallQueueDO>()
                .eq(OutcallQueueDO::getInstanceId, instanceId)
                .eq(OutcallQueueDO::getTaskCode, taskCode)
                .eq(OutcallQueueDO::getEnvId, envId)
                .orderByDesc(OutcallQueueDO::getGmtCreate));
        
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        
        return list.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
}
