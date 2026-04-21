package org.qianye.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.qianye.entity.OutboundCallTaskDO;

public interface OutboundCallTaskService extends IService<OutboundCallTaskDO> {
    /**
     * 根据instanceId和taskCode查询
     */
    OutboundCallTaskDO getByInstanceAndCode(String instanceId, String taskCode);

    /**
     * 根据instanceId和taskCode查询单个任务
     */
    OutboundCallTaskDO queryOneCallTaskByCode(String instanceId, String taskCode);

    /**
     * 分页查询指定实例的任务
     */
    Page<OutboundCallTaskDO> pageByInstance(String instanceId, int pageNum, int pageSize);

    /**
     * 分页查询处理中的任务
     */
    Page<OutboundCallTaskDO> pageProcessingTasks(int pageNum, int pageSize);

    /**
     * 分页查询处理中的任务（返回PageDTO）
     */
    PageDTO<OutboundCallTaskDO> queryProcessingTaskList(int pageNum, int pageSize);

    /**
     * 更新任务状态
     */
    boolean updateStatus(String instanceId, String taskCode, Integer status);
}
