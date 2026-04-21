package org.qianye.listener;

import org.qianye.DTO.MakeCallResponse;
import org.springframework.context.ApplicationEvent;

public class HangupEvent extends ApplicationEvent {
    private MakeCallResponse makeCallResponse;
    public HangupEvent(MakeCallResponse response) {
        super(response);
        this.makeCallResponse = response;
    }
}
