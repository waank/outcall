package org.qianye.DTO;

import org.qianye.engine.OutCallService;
import org.qianye.service.OutcallQueueGroupService;
import org.qianye.service.OutcallQueueService;
import org.qianye.service.impl.TaskScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Random;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 管理各种定时任务
 */
@Service
@Configuration
@EnableScheduling
@EnableAsync
public class OutCallTimer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutCallTimer.class);
    private static final Random RANDOM = new Random();
    @Resource
    private OutcallQueueGroupService outcallQueueGroupService;
    @Resource
    private OutcallQueueService outcallQueueService;
    @Resource
    private OutCallService outCallService;
    @Resource
    private TaskScanService taskScanService;
    // ----核心任务----

    /**
     * 生成0到maxDelaySeconds秒的随机延迟，防止并发过大
     */
    private void randomDelay(int maxDelaySeconds) {
        try {
            int delaySeconds = RANDOM.nextInt(maxDelaySeconds);
            if (delaySeconds > 0) {
                TimeUnit.SECONDS.sleep(delaySeconds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 外呼启动,每分钟执行一次
     */
    @Async("outCallTaskExecutor")
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void startOutCallTask() {
        randomDelay(60);
        outCallService.outCall();
    }

    /**
     * 任务扫描规划,每2分钟执行一次
     */
    @Async("outCallTaskExecutor")
    @Scheduled(cron = "0 0/2 * * * ? ")
    public void scanOutCallTask() {
        randomDelay(30);
        taskScanService.scanOutCallTask();
    }

    /**
     * 每5分钟检查队列组状态
     */
    @Async("outCallTaskExecutor")
    @Scheduled(cron = "0 0/5 * * * ? ")
    public void checkGroupStatus() {
        randomDelay(10);
        outcallQueueGroupService.checkGroupStatus();
    }
    // ----辅助任务----

    /**
     * 启动队列详情扫描,每5分钟扫描一次
     */
    @Async("outCallTaskExecutor")
    @Scheduled(cron = "0 0/5 * * * ? ")
    public void checkQueueDetail() {
        randomDelay(10);
        outcallQueueService.checkQueueStatus();
    }

    @Bean(name = "outCallTaskExecutor")
    public ThreadPoolTaskExecutor outCallTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("outCallTaskExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
