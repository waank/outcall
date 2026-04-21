package org.qianye.util;

import lombok.extern.slf4j.Slf4j;
import org.qianye.DTO.CallRecord;
import org.qianye.common.QueueStatus;
import org.qianye.common.OutCallResult;
import org.qianye.DTO.QueueDetailDTO;
import org.springframework.stereotype.Component;

/**
 * 外呼计划辅助类
 */
@Slf4j
@Component
public class OutPlanUtil {

    /**
     * 设置队列为停止状态
     */
    public void setQueueToStop(QueueDetailDTO queue, String reason) {
        queue.setStatus(QueueStatus.STOP);
        queue.getExtInfo().put(OutCallResult.STOP_REASON, reason);
        log.info("setQueueToStop, queueCode:{}, reason:{}", queue.getQueueCode(), reason);
    }

    /**
     * 设置队列为成功状态
     */
    public void setQueueToSuccess(QueueDetailDTO queue) {
        queue.setStatus(QueueStatus.STOP);
        queue.getExtInfo().put(OutCallResult.STOP_REASON, OutCallResult.EXECUTE_FINISH);
        log.info("setQueueToSuccess, queueCode:{}", queue.getQueueCode());
    }

    /**
     * 判断呼叫是否成功
     */
    public boolean isCallSuccessful(CallRecord callRecord) {
        // TODO: 根据通话记录判断呼叫是否成功
        return true;
    }
}
