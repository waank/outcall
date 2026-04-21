package org.qianye.DTO;

import lombok.Data;

/**
 * 外呼规则时间段模型
 */
@Data
public class OutboundRuleRangeModel {
    private Integer startTime;
    private Integer endTime;
}
