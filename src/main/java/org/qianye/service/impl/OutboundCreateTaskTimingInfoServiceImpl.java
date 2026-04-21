package org.qianye.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.qianye.entity.OutboundTimingInfoDO;
import org.qianye.mapper.OutboundTimingInfoMapper;
import org.qianye.service.OutboundCreateTaskTimingInfoService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 外呼任务择时信息服务实现类
 */
@Service
public class OutboundCreateTaskTimingInfoServiceImpl implements OutboundCreateTaskTimingInfoService {
    @Resource
    private OutboundTimingInfoMapper outboundTimingInfoMapper;

    @Override
    public List<OutboundTimingInfoDO> listOutboundTimingInfoDO(List<String> phones) {
        if (CollectionUtils.isEmpty(phones)) {
            return new ArrayList<>();
        }
        // 分批查询，避免IN子句过长
        List<OutboundTimingInfoDO> result = new ArrayList<>();
        int batchSize = 500;
        for (int i = 0; i < phones.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, phones.size());
            List<String> batch = phones.subList(i, endIndex);
            LambdaQueryWrapper<OutboundTimingInfoDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(OutboundTimingInfoDO::getPhone, batch);
            List<OutboundTimingInfoDO> batchResult = outboundTimingInfoMapper.selectList(wrapper);
            if (!CollectionUtils.isEmpty(batchResult)) {
                result.addAll(batchResult);
            }
        }
        return result;
    }
}
