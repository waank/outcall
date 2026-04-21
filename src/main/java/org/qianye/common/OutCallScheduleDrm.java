package org.qianye.common;

import org.springframework.stereotype.Component;

/**
 * 外呼调度DRM配置 - TODO: 接入配置中心
 */
@Component
public class OutCallScheduleDrm {
    public int getMakeCallMaxQueueSize() {
        return 1000;
    }

    public int getPollFirstGroupSize() {
        return 5;
    }

    public long getRateLimitWaitingTimeSecond() {
        return 10;
    }

    public long getFlowLimitSleepMs() {
        return 1000;
    }

    public int getMaxCallFlowRetries() {
        return 3;
    }

    public int getOutCallExecutorCorePoolSize() {
        return 20;
    }

    public int getCommonMakeCallCorePoolSize() {
        return 80;
    }

    public int getOutCallExecutorMaxPoolSize() {
        return 160;
    }

    public long getRequestRateControl() {
        return 0;
    }

    public boolean getStressTest() {
        return false;
    }

    public boolean getMockSleep() {
        return false;
    }

    public String getLargeOutCallTenantId() {
        return "";
    }

    public String getInterceptTodayRecallInstance() {
        return "";
    }

    /**
     * 队列查询批次大小
     */
    public int getQueueQueryBatch() {
        return 1000;
    }

    /**
     * 分组大小（用于子批次处理）
     */
    public int getGroupingSize() {
        return 100;
    }

    /**
     * 组编码数量限制（最小值）
     */
    public int getGroupCodeNumLimitMin() {
        return 10;
    }

    /**
     * 组编码数量限制
     */
    public int getGroupCodeNumLimit() {
        return 50;
    }

    /**
     * 规划组查询大小
     */
    public int getPlanningGroupQuerySize() {
        return 100;
    }

    /**
     * 队列组超时时间（分钟）
     */
    public int getQueueGroupTimeAge() {
        return 60;
    }

    /**
     *
     * @return 队列缓存的上限值
     */
    public long getCacheQueueGroupLimitNum() {
        return 10000;
    }
}
