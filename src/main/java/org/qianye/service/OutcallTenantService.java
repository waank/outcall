package org.qianye.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.qianye.entity.OutcallTenantDO;

public interface OutcallTenantService extends IService<OutcallTenantDO> {
    OutcallTenantDO getByTenantId(String tenantId, String envFlag);

    OutcallTenantDO getOrDefault(String tenantId, String envFlag);

    Page<OutcallTenantDO> pageByEnv(String envFlag, int pageNum, int pageSize);
}
