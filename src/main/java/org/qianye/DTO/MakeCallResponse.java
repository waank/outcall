package org.qianye.DTO;

import lombok.Data;

import java.util.Map;

@Data
public class MakeCallResponse {
    private String acid;
    private Boolean flowLimit = false;
    private String callee;
    private String caller;
    private String queueCode;
    private String instanceId;
    private String taskCode;
    private Map<String,String> params;
}
