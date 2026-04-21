package org.qianye.DTO;

import lombok.extern.slf4j.Slf4j;

/**
 * SBC监控类 - 用于监控外呼任务事件
 */
@Slf4j
public class SBCMonitor {
    /**
     * 定时任务事件上报
     */
    public static void timerCallTaskEvent(String eventName, boolean isOffline) {
        // TODO: 实现监控事件上报逻辑
        if (isOffline) {
            log.warn("Timer call task event: {} - server is offline", eventName);
        } else {
            log.debug("Timer call task event: {} - server is online", eventName);
        }
    }
}
