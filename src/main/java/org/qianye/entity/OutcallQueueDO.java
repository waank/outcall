package org.qianye.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 呼叫名单表
 */
@Data
@TableName("cc_outcall_queue")
public class OutcallQueueDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 实例id
     */
    private String instanceId;
    /**
     * 环境id
     */
    private String envId;
    /**
     * 队列code
     */
    private String queueCode;
    /**
     * 主叫
     */
    private String caller;
    /**
     * 被叫
     */
    private String callee;
    /**
     * 呼叫状态:waiting,running,success,fail,stop
     */
    private String queueStatus;
    /**
     * 关联的任务code
     */
    private String taskCode;
    /**
     * 关联的分组code
     */
    private String groupCode;
    /**
     * 通话id
     */
    private String acid;
    /**
     * 呼叫次数
     */
    private Integer callCount;
    /**
     * 呼叫开始时间
     */
    private Date callStartTime;
    /**
     * 呼叫结束时间
     */
    private Date callEndTime;
    /**
     * 扩展信息
     */
    private String extInfo;
    /**
     * 创建者
     */
    private String creator;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date gmtCreate;
    /**
     * 更新者
     */
    private String modifier;
    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date gmtModified;
}
