package org.qianye.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.qianye.entity.OutcallTenantDO;
import org.qianye.mapper.OutcallTenantMapper;
import org.qianye.service.OutcallTenantService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OutcallTenantServiceImpl extends ServiceImpl<OutcallTenantMapper, OutcallTenantDO>
        implements OutcallTenantService {

    private static final String DEFAULT_ENV = "default";

    @Override
    public OutcallTenantDO getByTenantId(String tenantId, String envFlag) {
        return getOne(new LambdaQueryWrapper<OutcallTenantDO>()
                .eq(OutcallTenantDO::getTenantId, tenantId)
                .eq(OutcallTenantDO::getEnvFlag, normalizeEnv(envFlag)));
    }

    @Override
    public OutcallTenantDO getOrDefault(String tenantId, String envFlag) {
        OutcallTenantDO tenantDO = getByTenantId(tenantId, envFlag);
        if (tenantDO != null) {
            return tenantDO;
        }
        return getByTenantId("DEFAULT", envFlag);
    }

    @Override
    public Page<OutcallTenantDO> pageByEnv(String envFlag, int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OutcallTenantDO>()
                        .eq(OutcallTenantDO::getEnvFlag, normalizeEnv(envFlag))
                        .orderByDesc(OutcallTenantDO::getGmtModified));
    }

    private String normalizeEnv(String envFlag) {
        return StringUtils.hasText(envFlag) ? envFlag : DEFAULT_ENV;
    }
}
