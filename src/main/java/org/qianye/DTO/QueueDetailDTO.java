package org.qianye.DTO;

import lombok.Data;
import org.qianye.common.QueueStatus;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class QueueDetailDTO {
    private String instanceId;
    private String taskCode;
    private String groupCode;
    private String queueCode;
    private String callee;
    private String caller;
    private String acid;
    private int callCount;
    private QueueStatus status;
    private Map<String, Object> extInfo = new HashMap<>();
    /**
     * 固定时间（字符串格式）
     */
    private String fixedTime;
    /**
     * 固定开始时间（Date格式）
     */
    private Date fixedStartTime;
    /**
     * 环境ID
     */
    private String envId;
    /**
     * 创建时间
     */
    private Date gmtCreate;
    /**
     * 修改时间
     */
    private Date gmtModified;
    /**
     * 呼叫开始时间
     */
    private Date callStartTime;
    /**
     * 呼叫结束时间
     */
    private Date callEndTime;
    /**
     * 上一次队列状态（用于状态更新）
     */
    private QueueStatus lastQueueStatus;
}
