package org.qianye.callback;

import org.qianye.DTO.QueueDetailDTO;
import org.qianye.entity.OutboundCallTaskDO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 外呼回调服务组合 - TODO: 完善实现
 */
@Component
public class OutCallBackServiceComposite {
    public List<QueueDetailDTO> findInvalidQueues(List<QueueDetailDTO> queueDetails, OutboundCallTaskDO task) {
        // TODO: 实现业务前置过滤
        return Collections.emptyList();
    }
}
