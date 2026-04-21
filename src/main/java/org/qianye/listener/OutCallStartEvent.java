package org.qianye.listener;

import lombok.Getter;
import org.qianye.entity.OutboundCallTaskDO;
import org.springframework.context.ApplicationEvent;

public class OutCallStartEvent extends ApplicationEvent {
    @Getter
    private OutboundCallTaskDO task;

    public OutCallStartEvent(OutboundCallTaskDO task) {
        super(task);
        this.task = task;
    }
}
