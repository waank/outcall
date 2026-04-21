package org.qianye.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import lombok.extern.slf4j.Slf4j;
import org.qianye.DTO.ScheduleConstants;
import org.qianye.cache.RedisLock;
import org.qianye.common.TracerRunnable;
import org.qianye.engine.OutCallExecutorService;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.service.OutboundCallTaskService;
import org.qianye.util.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TaskScanService {
    @Resource
    protected RedisLock redisLock;
    @Resource
    private TaskPlanService taskPlanService;
    @Resource
    private OutboundCallTaskService outboundCallTaskService;
    @Value("${env}")
    private String env;

    public void scanOutCallTask() {
        String lockKey = TaskScanService.class.getSimpleName() + ":" + env;
        try {
            boolean success = redisLock.tryLock(lockKey, "1", 60);
            if (!success) {
                LoggerUtil.info(log, "taskScan is running, skip it");
                return;
            }
            LoggerUtil.info(log, "taskScan start");
            long currentTimeMillis = System.currentTimeMillis();
            int pageNum = 1;
            int pageSize = ScheduleConstants.TASK_QUERY_MAX;
            PageDTO<OutboundCallTaskDO> processingTaskPage;
            do {
                Long startTime = System.currentTimeMillis();
                processingTaskPage = outboundCallTaskService.queryProcessingTaskList(pageNum, pageSize);
                if (CollectionUtils.isEmpty(processingTaskPage.getRecords())) {
                    LoggerUtil.info(log, "scanOutCallTaskQuery no processing task on page {}, ignore it", pageNum);
                    break;
                }
                log.info("scanOutCallTaskQuery processing task size:{},time:{}", processingTaskPage.getRecords().size(), startTime);
                for (OutboundCallTaskDO task : processingTaskPage.getRecords()) {
                    if (OutCallExecutorService.getPlanTaskThreadPool().getQueue().size() < 1000) {
                        CompletableFuture.runAsync(new TracerRunnable() {
                            @Override
                            public void doRun() {
                                taskPlanService.planTask(task);
                            }
                        }, OutCallExecutorService.getPlanTaskThreadPool());
                    }
                }
            } while (processingTaskPage.getRecords().size() == pageSize);
            LoggerUtil.info(log, "taskScan cost finish time:{}", System.currentTimeMillis() - currentTimeMillis);
        } catch (Exception e) {
            LoggerUtil.error(log, "scanOutCallTask error", e);
        } finally {
            redisLock.unlock(lockKey, "1");
        }
    }
}
