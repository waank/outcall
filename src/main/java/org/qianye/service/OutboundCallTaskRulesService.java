package org.qianye.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.qianye.entity.OutboundCallTaskRulesDO;

import java.util.List;

public interface OutboundCallTaskRulesService extends IService<OutboundCallTaskRulesDO> {
    /**
     * 根据instanceId和taskRulesCode查询
     */
    OutboundCallTaskRulesDO getByInstanceAndCode(String instanceId, String taskRulesCode);

    /**
     * 查询实例下所有启用的规则
     */
    List<OutboundCallTaskRulesDO> listEnabledByInstance(String instanceId);

    /**
     * 分页查询规则
     */
    Page<OutboundCallTaskRulesDO> pageByInstance(String instanceId, int pageNum, int pageSize);
}
