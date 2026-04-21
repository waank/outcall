package org.qianye.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 待呼叫名单队列表
 */
@Data
@TableName("cc_outcall_queue_group")
public class OutcallQueueGroupDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 实例id
     */
    private String instanceId;
    /**
     * 环境标记：pre/prod
     */
    private String envId;
    /**
     * 组code
     */
    private String groupCode;
    /**
     * 队列codes
     */
    private String queueCodes;
    /**
     * 任务code
     */
    private String taskCode;
    /**
     * 状态:waiting,proccessing,success,fail,stop
     */
    private String groupStatus;
    /**
     * 开始时间
     */
    private Date groupStartTime;
    /**
     * 结束时间
     */
    private Date groupEndTime;
    /**
     * 值越大，优先级越高
     */
    private Integer priority;
    /**
     * normal常规队列,fixedTime择时队列
     */
    private String groupType;
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
