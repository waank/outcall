package org.qianye.engine;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.qianye.cache.CacheClient;
import org.qianye.common.OutCallScheduleDrm;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.service.OutboundCallTaskService;
import org.qianye.util.LoggerUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 外呼限流服务
 */
@Slf4j
@Service
public class OutCallRateLimitServiceImpl {

    private static final String CPS_LIMIT = "cpsLimit";
    private static final String RATE_LIMIT_KEY_PREFIX = "outcall:rate:";

    @Resource
    private OutboundCallTaskService outboundCallTaskService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private OutCallScheduleDrm outCallScheduleDrm;

    /**
     * 限流判断
     *
     * @param groupCode 队列组编码
     * @param taskDO    任务对象
     * @return true-已达到限流阈值，false-未达到限流阈值
     */
    public boolean isReachedRateLimit(String groupCode, OutboundCallTaskDO taskDO) {
        LoggerUtil.info(log, "isReachedRateLimit,queueGroup:{},instanceId:{},taskCode:{}",
                groupCode, taskDO.getInstanceId(), taskDO.getTaskCode());
        boolean capsLimit = capsProcess(taskDO);
        if (!capsLimit) {
            LoggerUtil.info(log, "triggerFlowLimit,limitType:capsRateLimit,queueGroup:{},instanceId:{},taskCode:{}",
                    groupCode, taskDO.getInstanceId(), taskDO.getTaskCode());
            return true;
        }
        return false;
    }

    /**
     * CPS限流处理
     *
     * @param taskDO 任务对象
     * @return true-允许通过，false-触发限流
     */
    public boolean capsProcess(OutboundCallTaskDO taskDO) {
        int callerRateLimit = getCallerRateLimit(taskDO);
        String key = buildKey(taskDO);
        long cpsCount = cacheClient.incrBy(key, 1);
        cacheClient.expire(key, 2);
        if (cpsCount >= callerRateLimit) {
            LoggerUtil.info(log, "reachedRateLimit,taskCode:{},caller:{},rateLimit:{},cpsCount:{}",
                    taskDO.getTaskCode(), taskDO.getOutboundCaller(), callerRateLimit, cpsCount);
            return false;
        }
        return true;
    }

    /**
     * 获取调用方限流阈值
     *
     * @param taskDO 任务对象
     * @return 限流阈值
     */
    public Integer getCallerRateLimit(OutboundCallTaskDO taskDO) {
        try {
            Map<String, Object> extInfoMap = JSON.parseObject(taskDO.getExtInfo(), HashMap.class);
            Integer cpsLimit = extInfoMap != null ? (Integer) extInfoMap.get(CPS_LIMIT) : null;
            // 兜底100
            if (cpsLimit == null) {
                cpsLimit = 100;
            }
            LoggerUtil.info(log, "getCallerRateLimit taskCode:{},limit:{}", taskDO.getTaskCode(), cpsLimit);
            return cpsLimit;
        } catch (Exception e) {
            LoggerUtil.error(log, "getCallerRateLimit error", e);
            return 100;
        }
    }

    /**
     * 构建限流缓存Key
     *
     * @param taskDO 任务对象
     * @return 缓存Key
     */
    private String buildKey(OutboundCallTaskDO taskDO) {
        long currentTimeMillis = System.currentTimeMillis();
        int timeSecond = (int) (currentTimeMillis / 1000);
        return RATE_LIMIT_KEY_PREFIX + taskDO.getInstanceId() + ":" + taskDO.getTaskCode()+":"+timeSecond;
    }
}
