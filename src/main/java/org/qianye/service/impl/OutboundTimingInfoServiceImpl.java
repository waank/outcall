package org.qianye.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.qianye.entity.OutboundTimingInfoDO;
import org.qianye.mapper.OutboundTimingInfoMapper;
import org.qianye.service.OutboundTimingInfoService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OutboundTimingInfoServiceImpl extends ServiceImpl<OutboundTimingInfoMapper, OutboundTimingInfoDO>
        implements OutboundTimingInfoService {
    @Override
    public OutboundTimingInfoDO getByPhone(String phone) {
        return getOne(new LambdaQueryWrapper<OutboundTimingInfoDO>()
                .eq(OutboundTimingInfoDO::getPhone, phone));
    }
}
