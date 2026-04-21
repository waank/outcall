package org.qianye.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.qianye.entity.OutboundTimingInfoDO;

public interface OutboundTimingInfoService extends IService<OutboundTimingInfoDO> {
    /**
     * 根据手机号查询择时信息
     */
    OutboundTimingInfoDO getByPhone(String phone);
}
