package org.qianye.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 智能外呼任务规则表
 */
@Data
@TableName("cc_outbound_call_task_rules")
public class OutboundCallTaskRulesDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date gmtCreate;
    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date gmtModified;
    /**
     * 实例ID
     */
    private String instanceId;
    /**
     * 任务规则编码
     */
    private String taskRulesCode;
    /**
     * 规则名称
     */
    private String taskRulesName;
    /**
     * 定时任务执行时间区间
     */
    private String scheduleStartTime;
    /**
     * 定时任务执行区间
     */
    private String scheduleEndTime;
    /**
     * 任务执行时间的范围，List<CallTime >序列化的数据
     */
    private String taskRulesDetail;
    /**
     * 是否启用：0-启用，1-关闭
     */
    private Integer enableFlag;
    private String remarks;
    /**
     * 生效时间
     */
    private Date takeEffectTime;
    /**
     * 失效时间
     */
    private Date invalidTime;
    /**
     * 环境标志：pre prod sit
     */
    private String envFlag;
}
