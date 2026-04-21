package org.qianye.DTO;

import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * 通话记录
 */
@Data
public class CallRecord {
    private String acid;
    private String caller;
    private String callee;
    private String taskCode;
    private String queueCode;
    private Date startTime;
    private Date releaseTime;
    private Map<String, Object> flowData;
}
