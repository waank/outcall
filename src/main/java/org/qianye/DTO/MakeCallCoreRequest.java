package org.qianye.DTO;

import lombok.Data;

import java.util.Map;

@Data
public class MakeCallCoreRequest {
    private Map<String, Object> flowData;
    private String caller;
    private String callee;
    private String queueCode;
    private String instanceId;
    private String taskCode;
}
