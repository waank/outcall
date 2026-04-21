package org.qianye.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.qianye.common.Result;
import org.qianye.entity.OutboundCallTaskRulesDO;
import org.qianye.service.OutboundCallTaskRulesService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 外呼任务规则管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/outbound-task-rules")
public class OutboundCallTaskRulesController {
    @Resource
    private OutboundCallTaskRulesService outboundCallTaskRulesService;

    @PostMapping
    public Result<OutboundCallTaskRulesDO> create(@RequestBody OutboundCallTaskRulesDO entity) {
        outboundCallTaskRulesService.save(entity);
        return Result.success(entity);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outboundCallTaskRulesService.removeById(id);
        return Result.success();
    }

    @PutMapping
    public Result<OutboundCallTaskRulesDO> update(@RequestBody OutboundCallTaskRulesDO entity) {
        outboundCallTaskRulesService.updateById(entity);
        return Result.success(entity);
    }

    @GetMapping("/{id}")
    public Result<OutboundCallTaskRulesDO> getById(@PathVariable Long id) {
        return Result.success(outboundCallTaskRulesService.getById(id));
    }

    @GetMapping("/query")
    public Result<OutboundCallTaskRulesDO> getByInstanceAndCode(@RequestParam String instanceId,
                                                                @RequestParam String taskRulesCode) {
        return Result.success(outboundCallTaskRulesService.getByInstanceAndCode(instanceId, taskRulesCode));
    }

    @GetMapping("/enabled")
    public Result<List<OutboundCallTaskRulesDO>> listEnabled(@RequestParam String instanceId) {
        return Result.success(outboundCallTaskRulesService.listEnabledByInstance(instanceId));
    }

    @GetMapping("/page")
    public Result<Page<OutboundCallTaskRulesDO>> page(@RequestParam String instanceId,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(outboundCallTaskRulesService.pageByInstance(instanceId, pageNum, pageSize));
    }
}
