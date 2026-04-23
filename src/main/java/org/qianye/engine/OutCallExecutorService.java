package org.qianye.engine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.qianye.util.LoggerUtil;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;

@Service
@Slf4j
public class OutCallExecutorService {
    @Getter
    private static final ThreadPoolExecutor queueGroupthreadPool = new ThreadPoolExecutor(
            10, 40, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000), new ThreadPoolExecutor.DiscardPolicy());
    @Getter
    private static final ThreadPoolExecutor importQueueThreadPool = new ThreadPoolExecutor(
            4, 16, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(4000), new ThreadPoolExecutor.CallerRunsPolicy());
    @Getter
    private static final ThreadPoolExecutor retryThreadPool = new ThreadPoolExecutor(20, 40, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000), new ThreadPoolExecutor.CallerRunsPolicy());
    @Getter
    private static final ThreadPoolExecutor outCallThreadPool = new ThreadPoolExecutor(20, 64, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10000), new ThreadPoolExecutor.DiscardPolicy());
    @Getter
    private static final ThreadPoolExecutor largeMakeCallThreadPool = new ThreadPoolExecutor(20, 160, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(20000), new ThreadPoolExecutor.DiscardPolicy());
    @Getter
    private static final ThreadPoolExecutor planTaskThreadPool = new ThreadPoolExecutor(
            20, 40, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10000), new ThreadPoolExecutor.DiscardPolicy()
    );
    /**
     * 线程池，用于处理并发任务
     */
    @Getter
    private static final ThreadPoolExecutor commonMakeCallThreadPool = new ThreadPoolExecutor(
            80, 160, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10000));
    private ScheduledExecutorService monitorExecutor;

    @PostConstruct
    public void init() {
        startMonitor();
    }

    private void startMonitor() {
        monitorExecutor = Executors.newScheduledThreadPool(1);
        monitorExecutor.scheduleAtFixedRate(this::logThreadPoolStatus, 0, 10, TimeUnit.SECONDS);
        LoggerUtil.info(log, "OutCallService thread pool monitor started");
    }

    private void logThreadPoolStatus() {
        try {
            LoggerUtil.info(log,
                    "OutCallExecutorService, thread pool status -queueGroupthreadPool: ActiveCount={}, PoolSize={}, CorePoolSize={}, "
                            + "MaximumPoolSize={}, CompletedTaskCount={}, QueueSize={}"
                    , queueGroupthreadPool.getActiveCount()
                    , queueGroupthreadPool.getPoolSize()
                    , queueGroupthreadPool.getCorePoolSize()
                    , queueGroupthreadPool.getMaximumPoolSize()
                    , queueGroupthreadPool.getCompletedTaskCount()
                    , queueGroupthreadPool.getQueue().size());
            LoggerUtil.info(log,
                    "OutCallExecutorService, thread pool status -retryThreadPool: ActiveCount={}, PoolSize={}, CorePoolSize={}, "
                            + "MaximumPoolSize={}, CompletedTaskCount={}, QueueSize={}"
                    , retryThreadPool.getActiveCount()
                    , retryThreadPool.getPoolSize()
                    , retryThreadPool.getCorePoolSize()
                    , retryThreadPool.getMaximumPoolSize()
                    , retryThreadPool.getCompletedTaskCount()
                    , retryThreadPool.getQueue().size());
            LoggerUtil.info(log,
                    "OutCallExecutorService, thread pool status -importQueueThreadPool: ActiveCount={}, PoolSize={}, CorePoolSize={}, "
                            + "MaximumPoolSize={}, CompletedTaskCount={}, QueueSize={}"
                    , importQueueThreadPool.getActiveCount()
                    , importQueueThreadPool.getPoolSize()
                    , importQueueThreadPool.getCorePoolSize()
                    , importQueueThreadPool.getMaximumPoolSize()
                    , importQueueThreadPool.getCompletedTaskCount()
                    , importQueueThreadPool.getQueue().size());
            LoggerUtil.info(log,
                    "OutCallExecutorService, thread pool status -outCallThreadPool: ActiveCount={}, PoolSize={}, CorePoolSize={}, "
                            + "MaximumPoolSize={}, CompletedTaskCount={}, QueueSize={}"
                    , outCallThreadPool.getActiveCount()
                    , outCallThreadPool.getPoolSize()
                    , outCallThreadPool.getCorePoolSize()
                    , outCallThreadPool.getMaximumPoolSize()
                    , outCallThreadPool.getCompletedTaskCount()
                    , outCallThreadPool.getQueue().size());
            LoggerUtil.info(log,
                    "OutCallExecutorService, thread pool status -largeMakeCallThreadPool: ActiveCount={}, PoolSize={}, CorePoolSize={}, "
                            + "MaximumPoolSize={}, CompletedTaskCount={}, QueueSize={}"
                    , largeMakeCallThreadPool.getActiveCount()
                    , largeMakeCallThreadPool.getPoolSize()
                    , largeMakeCallThreadPool.getCorePoolSize()
                    , largeMakeCallThreadPool.getMaximumPoolSize()
                    , largeMakeCallThreadPool.getCompletedTaskCount()
                    , largeMakeCallThreadPool.getQueue().size());
            LoggerUtil.info(log,
                    "OutCallExecutorService, thread pool status -planTaskThreadPool: ActiveCount={}, PoolSize={}, CorePoolSize={}, "
                            + "MaximumPoolSize={}, CompletedTaskCount={}, QueueSize={}"
                    , planTaskThreadPool.getActiveCount()
                    , planTaskThreadPool.getPoolSize()
                    , planTaskThreadPool.getCorePoolSize()
                    , planTaskThreadPool.getMaximumPoolSize()
                    , planTaskThreadPool.getCompletedTaskCount()
                    , planTaskThreadPool.getQueue().size());
            LoggerUtil.info(log,
                    "OutCallExecutorService, thread pool status -commonOutCallThreadPool: ActiveCount={}, PoolSize={}, CorePoolSize={}, "
                            + "MaximumPoolSize={}, CompletedTaskCount={}, QueueSize={}"
                    , commonMakeCallThreadPool.getActiveCount()
                    , commonMakeCallThreadPool.getPoolSize()
                    , commonMakeCallThreadPool.getCorePoolSize()
                    , commonMakeCallThreadPool.getMaximumPoolSize()
                    , commonMakeCallThreadPool.getCompletedTaskCount()
                    , commonMakeCallThreadPool.getQueue().size());
        } catch (Exception e) {
            LoggerUtil.error(log, "Error occurred while logging thread pool status", e);
        }
    }

    @PreDestroy
    public void destroy() {
        // 关闭监控任务
        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            monitorExecutor.shutdown();
            try {
                if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        shutdownThreadPool(importQueueThreadPool, "importQueueThreadPool");
        shutdownThreadPool(retryThreadPool, "retryThreadPool");
        shutdownThreadPool(largeMakeCallThreadPool, "largeMakeCallThreadPool");
        shutdownThreadPool(outCallThreadPool, "outCallThreadPool");
        shutdownThreadPool(queueGroupthreadPool, "queueGroupthreadPool");
        shutdownThreadPool(planTaskThreadPool, "planTaskThreadPool");
        shutdownThreadPool(commonMakeCallThreadPool, "commonOutCallThreadPool");
        LoggerUtil.info(log, "OutCallService thread pool monitor stopped");
    }

    private void shutdownThreadPool(ThreadPoolExecutor threadPool, String threadPoolName) {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                    if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        LoggerUtil.warn(log, threadPoolName + " thread pool did not terminate gracefully");
                    }
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LoggerUtil.info(log, threadPoolName + " shutdown successfully");
        }
    }
}
