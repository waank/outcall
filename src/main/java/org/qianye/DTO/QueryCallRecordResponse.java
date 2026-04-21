package org.qianye.DTO;

import lombok.Data;

import java.util.List;

/**
 * 查询通话记录响应
 */
@Data
public class QueryCallRecordResponse {
    private List<CallRecord> list;
}
