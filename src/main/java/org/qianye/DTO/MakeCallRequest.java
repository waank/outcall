package org.qianye.DTO;

import lombok.Data;

@Data
public class MakeCallRequest {
    private String callType;
    private String taskRecordCode;
    private String instanceId;
    private String callee;
    private String caller;
    private String callerDisplay;
    private String calleeDisplay;
}
