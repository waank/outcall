package org.qianye.DTO;

import lombok.Data;
import org.qianye.common.GroupStatus;

import java.util.Date;
import java.util.List;

/**
 * 队列组查询请求
 */
@Data
public class QueueGroupRequest {
    private String instanceId;
    private String taskCode;
    private String envId;
    private String groupCode;
    private GroupStatus groupStatus;
    private List<String> groupTypes;
    private Date updateTimeStart;
    private Date updateTimeEnd;
    private Date groupStartTime;
    private int pageNum = 1;
    private int pageSize = 20;
}
