package org.qianye.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.qianye.common.GroupStatus;
import org.qianye.common.PageData;
import org.qianye.DTO.QueueGroupDTO;
import org.qianye.DTO.QueueGroupRequest;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.entity.OutcallQueueGroupDO;

import java.util.List;
import java.util.Map;

public interface OutcallQueueGroupService extends IService<OutcallQueueGroupDO> {
    /**
     * 根据instanceId和groupCode查询
     */
    OutcallQueueGroupDO getByInstanceAndCode(String instanceId, String envId, String groupCode);

    /**
     * 分页查询指定任务的队列组
     */
    Page<OutcallQueueGroupDO> pageByTask(String instanceId, String taskCode, String envId, int pageNum, int pageSize);

    /**
     * 更新队列组状态
     */
    boolean updateStatus(String instanceId, String envId, String groupCode, String status);

    /**
     * 检查队列组状态（定时任务调用）
     */
    void checkGroupStatus();

    List<QueueGroupDTO> queryQueueGroupByCodes(String instanceId, List<String> groupCodes);

    PageData<List<QueueGroupDTO>> pageQueueGroup(QueueGroupRequest request);

    QueueGroupDTO queryQueueGroupByCode(String instanceId, String groupCode);

    int updateQueueGroupStatus(String instanceId, String groupCode, GroupStatus status, Map<String, Object> extInfo);

    void updateQueueGroupStatus(List<QueueGroupDTO> request);

    int updateQueueGroupStatus(QueueGroupDTO queueGroupDTO);

    void stopQueueGroupAndQueue(QueueGroupDTO queueGroupDTO);

    /**
     * 插入队列组列表
     */
    void insertQueueGroup(List<QueueGroupDTO> queueGroups);

    /**
     * 添加队列组到最大容量限制
     */
    void addQueueGroup2Max(List<QueueGroupDTO> queueGroups);

    /**
     * 启动规划中的组
     */
    void startPlanningGroup(OutboundCallTaskDO task, boolean isFixedTimeGroup);

    /**
     * 检查固定时间组
     */
    void checkFixedGroup();

    /**
     * 更新队列组（根据组编码）
     */
    void updateQueueByGroupCode(QueueGroupDTO existingGroup);

    /**
     * 查询最新的固定时间组
     */
    QueueGroupDTO queryLastestFixTimeGroup(String instanceId, String taskCode, java.util.Date thisTime);
}
