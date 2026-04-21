package org.qianye.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.mapper.OutboundCallTaskMapper;
import org.qianye.service.OutboundCallTaskService;
import org.qianye.util.LoggerUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class OutboundCallTaskServiceImpl extends ServiceImpl<OutboundCallTaskMapper, OutboundCallTaskDO>
        implements OutboundCallTaskService {

    @Lazy
    @Resource
    private TaskPlanService taskPlanService;

    @Override
    public OutboundCallTaskDO getByInstanceAndCode(String instanceId, String taskCode) {
        return getOne(new LambdaQueryWrapper<OutboundCallTaskDO>()
                .eq(OutboundCallTaskDO::getInstanceId, instanceId)
                .eq(OutboundCallTaskDO::getTaskCode, taskCode));
    }

    @Override
    public OutboundCallTaskDO queryOneCallTaskByCode(String instanceId, String taskCode) {
        return getByInstanceAndCode(instanceId, taskCode);
    }

    @Override
    public Page<OutboundCallTaskDO> pageByInstance(String instanceId, int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OutboundCallTaskDO>()
                        .eq(OutboundCallTaskDO::getInstanceId, instanceId)
                        .orderByDesc(OutboundCallTaskDO::getGmtModified));
    }

    @Override
    public Page<OutboundCallTaskDO> pageProcessingTasks(int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OutboundCallTaskDO>()
                        .eq(OutboundCallTaskDO::getTaskStatus, 1)
                        .orderByDesc(OutboundCallTaskDO::getGmtModified));
    }

    @Override
    public PageDTO<OutboundCallTaskDO> queryProcessingTaskList(int pageNum, int pageSize) {
        Page<OutboundCallTaskDO> page = pageProcessingTasks(pageNum, pageSize);
        PageDTO<OutboundCallTaskDO> pageDTO = new PageDTO<>();
        pageDTO.setRecords(page.getRecords());
        pageDTO.setTotal(page.getTotal());
        pageDTO.setCurrent(page.getCurrent());
        pageDTO.setSize(page.getSize());
        return pageDTO;
    }

    @Override
    public boolean updateStatus(String instanceId, String taskCode, Integer status) {
        boolean updated = update(new LambdaUpdateWrapper<OutboundCallTaskDO>()
                .eq(OutboundCallTaskDO::getInstanceId, instanceId)
                .eq(OutboundCallTaskDO::getTaskCode, taskCode)
                .set(OutboundCallTaskDO::getTaskStatus, status));
        // 任务启动时立即触发调度
        if (updated && status != null && status == 1) {
            OutboundCallTaskDO taskDO = getByInstanceAndCode(instanceId, taskCode);
            if (taskDO != null) {
                LoggerUtil.info(log, "Task status updated to RUNNING, triggering planTask, instanceId:{}, taskCode:{}",
                        instanceId, taskCode);
                CompletableFuture.runAsync((()-> taskPlanService.planTask(taskDO)));
            }
        }
        return updated;
    }
}
