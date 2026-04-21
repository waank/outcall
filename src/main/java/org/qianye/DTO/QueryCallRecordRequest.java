package org.qianye.DTO;

import lombok.Data;

/**
 * 查询通话记录请求
 */
@Data
public class QueryCallRecordRequest {
    private String instanceId;
    private String acid;
    private String callee;
}
