package org.qianye.DTO;

import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
public class HangupResponse {
    private String acid;
    private String callee;
    private String caller;
    private String queueCode;
    private String taskCode;
    private String instanceId;
    private Map<String,String> params;
    private int status=200;

    private Date startTime=new Date();
    private Date endTime=new Date();
    /**
     * 音频地址
     */
    private String audiourl;
}
