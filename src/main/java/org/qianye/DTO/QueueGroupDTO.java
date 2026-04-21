package org.qianye.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.qianye.common.GroupStatus;
import org.qianye.common.QueueGroupType;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueGroupDTO {
    private String instanceId;
    private String taskCode;
    private String queueGroupCode;
    private GroupStatus groupStatus;
    private List<String> queueCodes;
    private CallTimeRange callTimeRange;
    private Date startTime;
    private Date endTime;
    private Map<String, Object> extInfo = new HashMap<>();
    /**
     * 环境ID
     */
    private String envId;
    /**
     * 组类型
     */
    private QueueGroupType groupType;
    /**
     * 组开始时间（用于固定时间组）
     */
    private Date groupStartTime;
}
