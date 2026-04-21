package org.qianye.DTO;

import lombok.Data;

@Data
public class OutboundTaskModel {
    private String taskCode;
    private String taskType;
    private String taskTransferType;
    private String taskRecordCode;
    private String taskTransferCode;
}
