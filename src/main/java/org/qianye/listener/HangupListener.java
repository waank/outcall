package org.qianye.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.qianye.DTO.HangupResponse;
import org.qianye.DTO.QueueDetailDTO;
import org.qianye.common.QueueStatus;
import org.qianye.service.OutcallQueueService;
import org.qianye.util.LoggerUtil;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class HangupListener {
    @Resource
    private OutcallQueueService queueDetailService;
    @Async
    @EventListener
    public void hangup(HangupResponse event) {
        try {
            LoggerUtil.info(log, "HangupListener, event:{} ", JSON.toJSONString(event));
            // 更新队列状态
            QueueDetailDTO queue = new QueueDetailDTO();
            queue.setQueueCode(event.getQueueCode());
            queue.setInstanceId(event.getInstanceId());
            queue.setTaskCode(event.getTaskCode());
            //  先查一次，防止并发 其他线程处理了该数据
            QueueDetailDTO queueInDb = queueDetailService.getOneByDetail(queue);
            if (queueInDb == null) {
                LoggerUtil.info(log, "hangupListener queueInDb is null,queue:{}", queue);
                return;
            }

            if (queueInDb.getStatus() == QueueStatus.SUCCESS || queueInDb.getStatus() == QueueStatus.STOP) {
                // 告警
                LoggerUtil.warn(log, "QueueInDbStatusAbnormal,queue is success or stop,instanceId:{},task:{},queue:{},callee:{},status:{}",
                        queueInDb.getInstanceId(), queueInDb.getTaskCode(), queueInDb.getQueueCode(), queueInDb.getCallee(), queueInDb.getStatus());
                return;

            }
            // 判断挂机原因是否正常
            QueueStatus status = determineQueueStatus(event);
            queueInDb.setStatus(status);

            log.info("hangupListenerUpdateByCode queue:{} ,acid:{}", queueInDb, event.getAcid());
            queueDetailService.updateByCode(queueInDb);

        } catch (Exception e) {
            LoggerUtil.error(log, "ccLimitDecr error", e);
        }
    }


    private QueueStatus determineQueueStatus(HangupResponse event) {
        boolean hasAnswered = event.getStartTime() != null && event.getEndTime() != null;
        return hasAnswered && event.getStatus() == 200 ? QueueStatus.SUCCESS : QueueStatus.STOP;

    }

}
