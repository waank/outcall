package org.qianye.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.util.Date;

@Data
@TableName("cc_outcall_tenant")
public class OutcallTenantDO {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Date gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date gmtModified;

    private String tenantId;

    private String tenantName;

    private String tenantType;

    private String envFlag;

    private Integer maxConcurrentSlots;

    private Integer enableFlag;

    private String extInfo;

    @Version
    private Long version;
}
