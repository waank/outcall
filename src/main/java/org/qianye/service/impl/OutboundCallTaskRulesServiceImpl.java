package org.qianye.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.qianye.entity.OutboundCallTaskRulesDO;
import org.qianye.mapper.OutboundCallTaskRulesMapper;
import org.qianye.service.OutboundCallTaskRulesService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class OutboundCallTaskRulesServiceImpl extends ServiceImpl<OutboundCallTaskRulesMapper, OutboundCallTaskRulesDO>
        implements OutboundCallTaskRulesService {
    @Override
    public OutboundCallTaskRulesDO getByInstanceAndCode(String instanceId, String taskRulesCode) {
        return getOne(new LambdaQueryWrapper<OutboundCallTaskRulesDO>()
                .eq(OutboundCallTaskRulesDO::getInstanceId, instanceId)
                .eq(OutboundCallTaskRulesDO::getTaskRulesCode, taskRulesCode));
    }

    @Override
    public List<OutboundCallTaskRulesDO> listEnabledByInstance(String instanceId) {
        return list(new LambdaQueryWrapper<OutboundCallTaskRulesDO>()
                .eq(OutboundCallTaskRulesDO::getInstanceId, instanceId)
                .eq(OutboundCallTaskRulesDO::getEnableFlag, 0));
    }

    @Override
    public Page<OutboundCallTaskRulesDO> pageByInstance(String instanceId, int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OutboundCallTaskRulesDO>()
                        .eq(OutboundCallTaskRulesDO::getInstanceId, instanceId)
                        .orderByDesc(OutboundCallTaskRulesDO::getGmtModified));
    }
}
