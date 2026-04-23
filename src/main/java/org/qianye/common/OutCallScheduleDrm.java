package org.qianye.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outcall schedule config.
 */
@Component
public class OutCallScheduleDrm {
    @Value("${outcall.large-tenant-ids:}")
    private String largeOutCallTenantIds;

    @Value("${outcall.large-task.min-queue-count:5000}")
    private int largeTaskMinQueueCount;

    @Value("${outcall.slot.global-capacity:160}")
    private int globalSlotCapacity;

    @Value("${outcall.slot.default-tenant-capacity:40}")
    private int defaultTenantSlotCapacity;

    @Value("${outcall.slot.normal-tenant-capacity:20}")
    private int normalTenantSlotCapacity;

    @Value("${outcall.slot.vip-tenant-capacity:100}")
    private int vipTenantSlotCapacity;

    @Value("${outcall.slot.default-task-capacity:20}")
    private int defaultTaskSlotCapacity;

    @Value("${outcall.slot.vip-task-capacity:80}")
    private int vipTaskSlotCapacity;

    @Value("${outcall.slot.normal-elastic.enabled:true}")
    private boolean normalElasticSlotEnabled;

    @Value("${outcall.slot.normal-elastic.global-idle-threshold:80}")
    private int normalElasticGlobalIdleThreshold;

    @Value("${outcall.slot.normal-elastic.tenant-extra-slots:10}")
    private int normalElasticTenantExtraSlots;

    @Value("${outcall.slot.normal-elastic.task-extra-slots:10}")
    private int normalElasticTaskExtraSlots;

    @Value("${outcall.slot.normal-elastic.max-tenant-capacity:40}")
    private int normalElasticMaxTenantCapacity;

    @Value("${outcall.slot.normal-elastic.max-task-capacity:30}")
    private int normalElasticMaxTaskCapacity;

    @Value("${outcall.intercept-today-recall-instance:}")
    private String interceptTodayRecallInstance;

    public int getMakeCallMaxQueueSize() {
        return 1000;
    }

    public int getPollFirstGroupSize() {
        return 5;
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

    public int getGlobalSlotCapacity() {
        return globalSlotCapacity;
    }

    public int getDefaultTenantSlotCapacity() {
        return defaultTenantSlotCapacity;
    }

    public int getNormalTenantSlotCapacity() {
        return normalTenantSlotCapacity;
    }

    public int getVipTenantSlotCapacity() {
        return vipTenantSlotCapacity;
    }

    public int getDefaultTaskSlotCapacity() {
        return defaultTaskSlotCapacity;
    }

    public boolean getStressTest() {
        return false;
    }

    public boolean getMockSleep() {
        return false;
    }

    public String getLargeOutCallTenantId() {
        return largeOutCallTenantIds;
    }

    public Set<String> getLargeOutCallTenantIds() {
        if (!StringUtils.hasText(largeOutCallTenantIds)) {
            return Collections.emptySet();
        }
        return Arrays.stream(StringUtils.tokenizeToStringArray(largeOutCallTenantIds, ",; \t\r\n"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    public int getLargeTaskMinQueueCount() {
        return largeTaskMinQueueCount;
    }

    public int getVipTaskSlotCapacity() {
        return vipTaskSlotCapacity;
    }

    public boolean isNormalElasticSlotEnabled() {
        return normalElasticSlotEnabled;
    }

    public int getNormalElasticGlobalIdleThreshold() {
        return normalElasticGlobalIdleThreshold;
    }

    public int getNormalElasticTenantExtraSlots() {
        return normalElasticTenantExtraSlots;
    }

    public int getNormalElasticTaskExtraSlots() {
        return normalElasticTaskExtraSlots;
    }

    public int getNormalElasticMaxTenantCapacity() {
        return normalElasticMaxTenantCapacity;
    }

    public int getNormalElasticMaxTaskCapacity() {
        return normalElasticMaxTaskCapacity;
    }

    public String getInterceptTodayRecallInstance() {
        return interceptTodayRecallInstance;
    }

    public int getQueueQueryBatch() {
        return 1000;
    }

    public int getGroupingSize() {
        return 100;
    }

    public int getGroupCodeNumLimitMin() {
        return 10;
    }

    public int getGroupCodeNumLimit() {
        return 50;
    }

    public int getPlanningGroupQuerySize() {
        return 100;
    }

    public int getQueueGroupTimeAge() {
        return 60;
    }

    public long getCacheQueueGroupLimitNum() {
        return 10000;
    }
}
