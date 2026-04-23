package org.qianye.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.qianye.common.Result;
import org.qianye.entity.OutcallTenantDO;
import org.qianye.service.OutcallTenantService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/v1/outcall-tenant")
public class OutcallTenantController {
    @Resource
    private OutcallTenantService outcallTenantService;

    @PostMapping
    public Result<OutcallTenantDO> create(@RequestBody OutcallTenantDO entity) {
        outcallTenantService.save(entity);
        return Result.success(entity);
    }

    @PutMapping
    public Result<OutcallTenantDO> update(@RequestBody OutcallTenantDO entity) {
        outcallTenantService.updateById(entity);
        return Result.success(entity);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outcallTenantService.removeById(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<OutcallTenantDO> getById(@PathVariable Long id) {
        return Result.success(outcallTenantService.getById(id));
    }

    @GetMapping("/query")
    public Result<OutcallTenantDO> getByTenantId(@RequestParam String tenantId,
                                                 @RequestParam(required = false) String envFlag) {
        return Result.success(outcallTenantService.getByTenantId(tenantId, envFlag));
    }

    @GetMapping("/page")
    public Result<Page<OutcallTenantDO>> page(@RequestParam(required = false) String envFlag,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(outcallTenantService.pageByEnv(envFlag, pageNum, pageSize));
    }
}
