package org.qianye.listener;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.qianye.cache.CacheClient;
import org.qianye.common.TaskStatusEnum;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.service.OutboundCallTaskService;
import org.qianye.util.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class OutCallEndEventListener {
    @Resource
    private OutboundCallTaskService outboundCallTaskService;
    @Resource
    private CacheClient cacheClient;
    @Value("${env}")
    private String env;

    @Async
    @EventListener
    public void handleOutCallEndEvent(OutCallEndEvent event) {
        LoggerUtil.info(log, "OutCallEndEventListener handleOutCallEndEvent:{}", JSON.toJSONString(event));
        OutboundCallTaskDO task = event.getTask();
        OutboundCallTaskDO callTaskDO = outboundCallTaskService.queryOneCallTaskByCode(task.getInstanceId(), task.getTaskCode());
        if (callTaskDO == null) {
            LoggerUtil.warn(log, "task not found, instanceId:{}, taskCode:{}", task.getInstanceId(), task.getTaskCode());
            return;
        }
        if (callTaskDO.getTaskStatus().equals(TaskStatusEnum.RUNNING.getCode())) {
            outboundCallTaskService.updateStatus(task.getInstanceId(), task.getTaskCode(), TaskStatusEnum.FINISHED.getCode());
            LoggerUtil.info(log, "OutCallEnd,update task status to finish, instanceId:{}, taskCode:{},taskName:{},env:{}",
                    task.getInstanceId(), task.getTaskCode(), task.getTaskName(), env);
            LoggerUtil.info(log, "task end ,delete cache, instanceId:{}, taskCode:{}",
                    task.getInstanceId(), task.getTaskCode());
        }
    }
}