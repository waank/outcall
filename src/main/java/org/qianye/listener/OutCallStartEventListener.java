package org.qianye.listener;

import lombok.extern.slf4j.Slf4j;
import org.qianye.cache.CacheClient;
import org.qianye.service.OutboundCallTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class OutCallStartEventListener {
    @Resource
    private OutboundCallTaskService outboundCallTaskService;
    @Resource
    private CacheClient cacheClient;
    @Value("${env}")
    private String env;

    @Async
    @EventListener
    public void handleOutCallStartEvent(OutCallStartEvent event) {
        log.info("start outcall start,instanceId:{},taskCode:{}", event.getTask().getInstanceId(),
                event.getTask().getTaskCode());
    }
}