package org.qianye.engine;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.qianye.DTO.QueueDetailDTO;
import org.qianye.cache.CacheClient;
import org.qianye.cache.RedisLock;
import org.qianye.common.OutCallScheduleDrm;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.entity.OutcallTenantDO;
import org.qianye.service.OutcallTenantService;
import org.qianye.util.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OutCallSlotServiceImpl {
    private static final String DEFAULT_ENV = "default";
    private static final String DEFAULT_TENANT = "DEFAULT";
    private static final String TENANT_TYPE_VIP = "VIP";
    private static final String TENANT_TYPE_NORMAL = "NORMAL";
    private static final String SLOT_KEY_PREFIX = "outcall:slot:";
    private static final String SLOT_LOCK_PREFIX = "outcall:slot:lock:";
    private static final int SLOT_LOCK_EXPIRE_SECONDS = 3;
    private static final int SLOT_LOCK_WAIT_MILLISECONDS = 200;
    private static final int SLOT_CACHE_EXPIRE_SECONDS = 24 * 3600;
    private static final String TASK_SLOT_CAPACITY = "taskSlotCapacity";
    private static final String TENANT_SLOT_CAPACITY = "tenantSlotCapacity";
    private static final String GLOBAL_SLOT_CAPACITY = "globalSlotCapacity";

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisLock redisLock;

    @Resource
    private OutCallScheduleDrm outCallScheduleDrm;

    @Resource
    private OutcallTenantService outcallTenantService;

    public boolean tryAcquireSlots(OutboundCallTaskDO taskDO, QueueDetailDTO queueDetailDTO) {
        List<SlotContext> slotContexts = buildSlotContexts(taskDO);
        slotContexts.sort(Comparator.comparing(SlotContext::getLockKey));
        String lockValue = Thread.currentThread().getName() + ":" + System.nanoTime();
        List<SlotContext> lockedContexts = new ArrayList<>();
        try {
            for (SlotContext slotContext : slotContexts) {
                boolean locked = redisLock.lockWaitTime(slotContext.getLockKey(), lockValue,
                        SLOT_LOCK_EXPIRE_SECONDS, SLOT_LOCK_WAIT_MILLISECONDS);
                if (!locked) {
                    LoggerUtil.info(log, "slotLockBusy,instanceId:{},taskCode:{},queueCode:{},slotType:{}",
                            taskDO.getInstanceId(), taskDO.getTaskCode(), queueDetailDTO.getQueueCode(), slotContext.getName());
                    return false;
                }
                lockedContexts.add(slotContext);
            }
            for (SlotContext slotContext : slotContexts) {
                int used = getUsedSlots(slotContext.getSlotKey());
                if (used >= slotContext.getCapacity()) {
                    LoggerUtil.info(log, "slotLimitReached,instanceId:{},taskCode:{},queueCode:{},slotType:{},used:{},capacity:{}",
                            taskDO.getInstanceId(), taskDO.getTaskCode(), queueDetailDTO.getQueueCode(),
                            slotContext.getName(), used, slotContext.getCapacity());
                    return false;
                }
            }
            for (SlotContext slotContext : slotContexts) {
                incrementSlots(slotContext.getSlotKey());
            }
            LoggerUtil.info(log, "slotAcquireSuccess,instanceId:{},taskCode:{},queueCode:{},slotKeys:{}",
                    taskDO.getInstanceId(), taskDO.getTaskCode(), queueDetailDTO.getQueueCode(),
                    slotContexts.stream().map(SlotContext::getSlotKey).collect(java.util.stream.Collectors.toList()));
            return true;
        } catch (Exception e) {
            LoggerUtil.error(log, e, "tryAcquireSlots error,instanceId:{},taskCode:{},queueCode:{}",
                    taskDO.getInstanceId(), taskDO.getTaskCode(), queueDetailDTO.getQueueCode());
            return false;
        } finally {
            for (int i = lockedContexts.size() - 1; i >= 0; i--) {
                redisLock.unlock(lockedContexts.get(i).getLockKey(), lockValue);
            }
        }
    }

    public void releaseSlots(QueueDetailDTO queueDetailDTO) {
        if (queueDetailDTO == null) {
            return;
        }
        List<SlotContext> slotContexts = buildSlotContexts(queueDetailDTO);
        for (SlotContext slotContext : slotContexts) {
            decrementSlots(slotContext.getSlotKey());
        }
        LoggerUtil.info(log, "slotReleaseSuccess,instanceId:{},taskCode:{},queueCode:{},acid:{}",
                queueDetailDTO.getInstanceId(), queueDetailDTO.getTaskCode(), queueDetailDTO.getQueueCode(), queueDetailDTO.getAcid());
    }

    private List<SlotContext> buildSlotContexts(OutboundCallTaskDO taskDO) {
        String envFlag = resolveEnvFlag(taskDO.getEnvFlag());
        String tenantId = resolveTenantId(taskDO.getInstanceId());
        Map<String, Object> taskExt = parseExtInfo(taskDO.getExtInfo());
        OutcallTenantDO tenantDO = outcallTenantService != null ? outcallTenantService.getOrDefault(tenantId, envFlag) : null;
        int globalCapacity = resolveCapacity(taskExt.get(GLOBAL_SLOT_CAPACITY), outCallScheduleDrm.getGlobalSlotCapacity());
        int tenantCapacity = resolveCapacity(taskExt.get(TENANT_SLOT_CAPACITY), resolveTenantSlotCapacity(tenantDO));
        int taskCapacity = resolveCapacity(taskExt.get(TASK_SLOT_CAPACITY), resolveTaskSlotCapacity(tenantDO));
        if (canUseNormalElasticSlots(tenantDO, envFlag, globalCapacity, taskExt)) {
            tenantCapacity = Math.min(outCallScheduleDrm.getNormalElasticMaxTenantCapacity(),
                    tenantCapacity + outCallScheduleDrm.getNormalElasticTenantExtraSlots());
            taskCapacity = Math.min(outCallScheduleDrm.getNormalElasticMaxTaskCapacity(),
                    taskCapacity + outCallScheduleDrm.getNormalElasticTaskExtraSlots());
        }

        List<SlotContext> slotContexts = new ArrayList<>(3);
        slotContexts.add(new SlotContext("global",
                SLOT_KEY_PREFIX + "global:" + envFlag,
                SLOT_LOCK_PREFIX + "global:" + envFlag,
                globalCapacity));
        slotContexts.add(new SlotContext("tenant",
                SLOT_KEY_PREFIX + "tenant:" + envFlag + ":" + tenantId,
                SLOT_LOCK_PREFIX + "tenant:" + envFlag + ":" + tenantId,
                tenantCapacity));
        slotContexts.add(new SlotContext("task",
                SLOT_KEY_PREFIX + "task:" + envFlag + ":" + taskDO.getInstanceId() + ":" + taskDO.getTaskCode(),
                SLOT_LOCK_PREFIX + "task:" + envFlag + ":" + taskDO.getInstanceId() + ":" + taskDO.getTaskCode(),
                taskCapacity));
        return slotContexts;
    }

    private List<SlotContext> buildSlotContexts(QueueDetailDTO queueDetailDTO) {
        String envFlag = resolveEnvFlag(queueDetailDTO.getEnvId());
        String tenantId = resolveTenantId(queueDetailDTO.getInstanceId());
        List<SlotContext> slotContexts = new ArrayList<>(3);
        slotContexts.add(new SlotContext("global", SLOT_KEY_PREFIX + "global:" + envFlag, null, 0));
        slotContexts.add(new SlotContext("tenant", SLOT_KEY_PREFIX + "tenant:" + envFlag + ":" + tenantId, null, 0));
        slotContexts.add(new SlotContext("task",
                SLOT_KEY_PREFIX + "task:" + envFlag + ":" + queueDetailDTO.getInstanceId() + ":" + queueDetailDTO.getTaskCode(),
                null, 0));
        return slotContexts;
    }

    private int getUsedSlots(String key) {
        String value = cacheClient.get(key);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            LoggerUtil.warn(log, "invalid slot counter,key:{},value:{}", key, value);
            return 0;
        }
    }

    private void incrementSlots(String key) {
        cacheClient.incrBy(key, 1);
        cacheClient.expire(key, SLOT_CACHE_EXPIRE_SECONDS);
    }

    private void decrementSlots(String key) {
        long current = cacheClient.incrBy(key, -1);
        if (current < 0) {
            cacheClient.put(key, "0", SLOT_CACHE_EXPIRE_SECONDS);
        } else {
            cacheClient.expire(key, SLOT_CACHE_EXPIRE_SECONDS);
        }
    }

    private Map<String, Object> parseExtInfo(String extInfo) {
        if (!StringUtils.hasText(extInfo)) {
            return new HashMap<>(0);
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(extInfo, HashMap.class);
            return parsed != null ? parsed : new HashMap<>(0);
        } catch (Exception e) {
            LoggerUtil.warn(log, "parseExtInfo error, extInfo:{}", extInfo, e);
            return new HashMap<>(0);
        }
    }

    private int resolveCapacity(Object rawValue, Integer defaultValue) {
        if (rawValue instanceof Number) {
            return Math.max(1, ((Number) rawValue).intValue());
        }
        if (rawValue instanceof String && StringUtils.hasText((String) rawValue)) {
            try {
                return Math.max(1, Integer.parseInt(((String) rawValue).trim()));
            } catch (NumberFormatException e) {
                LoggerUtil.warn(log, "invalid slot capacity value:{}", rawValue);
            }
        }
        return Math.max(1, defaultValue == null ? 1 : defaultValue);
    }

    private String resolveEnvFlag(String envFlag) {
        return StringUtils.hasText(envFlag) ? envFlag : DEFAULT_ENV;
    }

    private String resolveTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT;
    }

    private int resolveTenantSlotCapacity(OutcallTenantDO tenantDO) {
        if (tenantDO == null) {
            return outCallScheduleDrm.getDefaultTenantSlotCapacity();
        }
        if (tenantDO.getMaxConcurrentSlots() != null && tenantDO.getMaxConcurrentSlots() > 0) {
            return tenantDO.getMaxConcurrentSlots();
        }
        String tenantType = tenantDO.getTenantType();
        if (!StringUtils.hasText(tenantType)) {
            return outCallScheduleDrm.getDefaultTenantSlotCapacity();
        }
        if (TENANT_TYPE_VIP.equalsIgnoreCase(tenantType)) {
            return outCallScheduleDrm.getVipTenantSlotCapacity();
        }
        if (TENANT_TYPE_NORMAL.equalsIgnoreCase(tenantType)) {
            return outCallScheduleDrm.getNormalTenantSlotCapacity();
        }
        return outCallScheduleDrm.getDefaultTenantSlotCapacity();
    }

    private int resolveTaskSlotCapacity(OutcallTenantDO tenantDO) {
        if (isVipTenant(tenantDO)) {
            return outCallScheduleDrm.getVipTaskSlotCapacity();
        }
        return outCallScheduleDrm.getDefaultTaskSlotCapacity();
    }

    private boolean canUseNormalElasticSlots(OutcallTenantDO tenantDO, String envFlag, int globalCapacity,
                                             Map<String, Object> taskExt) {
        if (!outCallScheduleDrm.isNormalElasticSlotEnabled()) {
            return false;
        }
        if (!isNormalTenant(tenantDO)) {
            return false;
        }
        if (taskExt.containsKey(TENANT_SLOT_CAPACITY) || taskExt.containsKey(TASK_SLOT_CAPACITY)) {
            return false;
        }
        int globalUsedSlots = getUsedSlots(SLOT_KEY_PREFIX + "global:" + envFlag);
        int globalIdleSlots = Math.max(0, globalCapacity - globalUsedSlots);
        return globalIdleSlots >= outCallScheduleDrm.getNormalElasticGlobalIdleThreshold();
    }

    private boolean isVipTenant(OutcallTenantDO tenantDO) {
        return tenantDO != null && TENANT_TYPE_VIP.equalsIgnoreCase(tenantDO.getTenantType());
    }

    private boolean isNormalTenant(OutcallTenantDO tenantDO) {
        return tenantDO == null || !StringUtils.hasText(tenantDO.getTenantType())
                || TENANT_TYPE_NORMAL.equalsIgnoreCase(tenantDO.getTenantType());
    }

    @Data
    private static class SlotContext {
        private final String name;
        private final String slotKey;
        private final String lockKey;
        private final int capacity;
    }
}
