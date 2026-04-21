package org.qianye.DTO;

import org.qianye.entity.OutboundCallTaskDO;
import org.springframework.stereotype.Component;

/**
 * 当日重复呼叫拦截 - TODO: 完善实现
 */
@Component
public class InterceptTodayRecallService {
    public boolean isTodayRecall(QueueDetailDTO queue, OutboundCallTaskDO taskDO) {
        // TODO: 实现当日重复呼叫判断
        return false;
    }
}
