package org.qianye.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 外呼择时信息
 */
@Data
@TableName("cc_outbound_timing_info")
public class OutboundTimingInfoDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date gmtModified;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date gmtCreate;
    /**
     * 手机号
     */
    private String phone;
    /**
     * 实例id
     */
    private String instanceId;
    /**
     * 时间段
     */
    private String timing;
    /**
     * 关联的来源业务id
     */
    private String bizId;
    /**
     * 来源
     */
    private String source;
    /**
     * 用户标签，多个标签以逗号分割
     */
    private String tag;
    /**
     * 扩展参数
     */
    private String extInfo;
}
