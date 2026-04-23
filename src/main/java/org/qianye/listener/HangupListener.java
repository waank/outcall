package org.qianye.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.qianye.DTO.HangupResponse;
import org.qianye.DTO.QueueDetailDTO;
import org.qianye.common.QueueStatus;
import org.qianye.engine.OutCallSlotServiceImpl;
import org.qianye.service.OutcallQueueService;
import org.qianye.util.LoggerUtil;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class HangupListener {
    private static final String SLOT_RELEASED = "slotReleased";

    @Resource
    private OutcallQueueService queueDetailService;
    @Resource
    private OutCallSlotServiceImpl outCallSlotService;

    @Async
    @EventListener
    public void hangup(HangupResponse event) {
        try {
            LoggerUtil.info(log, "HangupListener, event:{} ", JSON.toJSONString(event));
            QueueDetailDTO queue = new QueueDetailDTO();
            queue.setQueueCode(event.getQueueCode());
            queue.setInstanceId(event.getInstanceId());
            queue.setTaskCode(event.getTaskCode());
            QueueDetailDTO queueInDb = queueDetailService.getOneByDetail(queue);
            if (queueInDb == null) {
                LoggerUtil.info(log, "hangupListener queueInDb is null,queue:{}", queue);
                return;
            }
            if (Boolean.TRUE.equals(queueInDb.getExtInfo().get(SLOT_RELEASED))) {
                LoggerUtil.info(log, "hangupListener slot already released,queueCode:{},acid:{}",
                        queueInDb.getQueueCode(), event.getAcid());
                return;
            }
            if (queueInDb.getStatus() == QueueStatus.SUCCESS || queueInDb.getStatus() == QueueStatus.STOP) {
                LoggerUtil.warn(log, "QueueInDbStatusAbnormal,queue is success or stop,instanceId:{},task:{},queue:{},callee:{},status:{}",
                        queueInDb.getInstanceId(), queueInDb.getTaskCode(), queueInDb.getQueueCode(),
                        queueInDb.getCallee(), queueInDb.getStatus());
            }

            queueInDb.setStatus(determineQueueStatus(event));
            outCallSlotService.releaseSlots(queueInDb);
            queueInDb.getExtInfo().put(SLOT_RELEASED, true);
            log.info("hangupListenerUpdateByCode queue:{} ,acid:{}", queueInDb, event.getAcid());
            queueDetailService.updateByCode(queueInDb);
        } catch (Exception e) {
            LoggerUtil.error(log, "slotReleaseOnHangup error", e);
        }
    }

    private QueueStatus determineQueueStatus(HangupResponse event) {
        boolean hasAnswered = event.getStartTime() != null && event.getEndTime() != null;
        return hasAnswered && event.getStatus() == 200 ? QueueStatus.SUCCESS : QueueStatus.STOP;
    }
}
