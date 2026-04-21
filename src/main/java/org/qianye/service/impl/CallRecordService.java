package org.qianye.service.impl;

import org.qianye.DTO.QueryCallRecordRequest;
import org.qianye.DTO.QueryCallRecordResponse;
import org.springframework.stereotype.Service;

/**
 * 通话记录服务
 */
@Service
public class CallRecordService {
    /**
     * 查询通话记录列表
     */
    public QueryCallRecordResponse queryCallRecordList(QueryCallRecordRequest request) {
        // TODO: 实现通话记录查询逻辑
        return new QueryCallRecordResponse();
    }
}
