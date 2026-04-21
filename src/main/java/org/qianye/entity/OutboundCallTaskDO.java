package org.qianye.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 智能外呼任务表
 */
@Data
@TableName("cc_outbound_call_task")
public class OutboundCallTaskDO {
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
     * 任务编码
     */
    private String taskCode;
    /**
     * 任务名称
     */
    private String taskName;
    /**
     * 实例ID
     */
    private String instanceId;
    /**
     * 任务规则code
     */
    private String taskRulesCode;
    /**
     * 任务类型：AUTO_CALL/OUTBOUND_CALL/IVR_CALL
     */
    private String taskType;
    /**
     * 实际执行的对象（坐席/技能组/IVRCode）
     */
    private String transferCode;
    /**
     * 任务状态 0-启用，1-执行中，2-暂停，4-终止
     */
    private Integer taskStatus;
    /**
     * 主叫号码
     */
    private String outboundCaller;
    /**
     * 转接类型
     */
    private String taskTransferType;
    /**
     * 环境标志：pre prod sit
     */
    private String envFlag;
    /**
     * 扩展参数
     */
    private String extInfo;
    /**
     * 收单状态
     */
    private String acquireStatus;
    /**
     * 版本号
     */
    @Version
    private Long version;
}
