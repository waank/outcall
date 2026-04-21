package org.qianye.service;

import org.qianye.entity.OutboundTimingInfoDO;

import java.util.List;

/**
 * 外呼任务择时信息服务接口
 */
public interface OutboundCreateTaskTimingInfoService {
    /**
     * 根据手机号列表查询择时信息
     */
    List<OutboundTimingInfoDO> listOutboundTimingInfoDO(List<String> phones);
}
