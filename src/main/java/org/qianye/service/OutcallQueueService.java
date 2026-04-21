package org.qianye.service;

import org.qianye.DTO.QueueDetailRequest;
import org.qianye.common.PageData;
import org.qianye.DTO.QueueDetailDTO;

import java.util.List;

/**
 * 队列详情服务 -
 */
public interface OutcallQueueService {
    List<QueueDetailDTO> queueDetailDTOList(QueueDetailRequest request);

    PageData<List<QueueDetailDTO>> queryPage(QueueDetailRequest request);

    QueueDetailDTO getByCode(String instanceId, String queueCode);

    QueueDetailDTO getOneByDetail(QueueDetailDTO queueDetailDto);

    List<QueueDetailDTO> getByCodes(String instanceId, List<String> queueCodes);

    void updateByLastStatus(QueueDetailDTO queue);

    void updateByCode(QueueDetailDTO queueDetail);

    void checkQueueStatus();

    /**
     * 更新队列列表
     */
    void updateQueues(List<QueueDetailDTO> queues);

    /**
     * 根据通话记录更新处理中队列状态
     */
    void updateProcessingQueueStatusByCallRecord(List<QueueDetailDTO> processingQueues);

    /**
     * 更新规划中队列状态并找出需要重试的队列
     */
    List<QueueDetailDTO> updatePlanningQueueAndFindRetryQueue(List<QueueDetailDTO> planningQueues);

    /**
     * 根据instanceId和queueCode查询
     */
    QueueDetailDTO getByInstanceAndCode(String instanceId, String queueCode, String envId);

    /**
     * 更新队列状态
     */
    boolean updateStatus(String instanceId, String queueCode, String envId, String status);

    void removeById(Long id);

    void batchInsertQueue(List<QueueDetailDTO> queueDetailDTOList);

    /**
     * 根据任务编码查询队列列表
     */
    List<QueueDetailDTO> getByTaskCode(String instanceId, String taskCode, String envId);
}
