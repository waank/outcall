package org.qianye.DTO;

import lombok.Data;
import org.qianye.common.QueueStatus;

import java.util.Date;
import java.util.List;

@Data
public class QueueDetailRequest {
    private String instanceId;
    private String taskCode;
    private String env;
    private int pageNum;
    private int pageSize;
    private QueueStatus status;
    private Date endTime;
    private Date startTime;
    /**
     * 被叫号码列表
     */
    private List<String> calleeList;
    /**
     * 队列状态列表（用于IN查询）
     */
    private List<String> queueStatusList;
}
