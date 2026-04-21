package org.qianye.listener;

import lombok.Getter;
import org.qianye.entity.OutboundCallTaskDO;
import org.springframework.context.ApplicationEvent;

public class OutCallEndEvent extends ApplicationEvent {
    @Getter
    private OutboundCallTaskDO task;

    public OutCallEndEvent(OutboundCallTaskDO task) {
        super(task);
        this.task = task;
    }
}
