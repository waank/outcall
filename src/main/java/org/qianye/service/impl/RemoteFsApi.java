package org.qianye.service.impl;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.qianye.DTO.HangupResponse;
import org.qianye.DTO.MakeCallCoreRequest;
import org.qianye.DTO.MakeCallResponse;
import org.qianye.common.RPCResult;
import org.qianye.util.UuidUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * 远程 API -
 */
@Component
@Slf4j
public class RemoteFsApi {
    @Resource
    private ApplicationEventPublisher eventPublisher;
    public RPCResult<MakeCallResponse> makeCall(MakeCallCoreRequest request) throws InterruptedException {
        RPCResult<MakeCallResponse> result = new RPCResult<>();
        MakeCallResponse makeCallResponse = new MakeCallResponse();
        makeCallResponse.setAcid(UuidUtil.generateShortUuid());
        makeCallResponse.setCaller(request.getCaller());
        makeCallResponse.setQueueCode(request.getQueueCode());
        makeCallResponse.setInstanceId(request.getInstanceId());
        makeCallResponse.setTaskCode(request.getTaskCode());
        result.setData(makeCallResponse);

        HangupResponse hangupResponse = JSON.parseObject(JSON.toJSONString(makeCallResponse), HangupResponse.class);
        CompletableFuture.runAsync(() -> {
            try {
                // 模拟通话结束的事件可能在接口响应的前或后返回。
                int delayMills = new Random().nextInt(100);
                Thread.sleep(50 + delayMills);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
            eventPublisher.publishEvent(hangupResponse);
        });
        Thread.sleep(100);
        return result;
    }
}
