package org.qianye.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.qianye.common.Result;
import org.qianye.entity.OutboundCallTaskDO;
import org.qianye.service.OutboundCallTaskService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 外呼任务管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/outbound-task")
public class OutboundCallTaskController {
    @Resource
    private OutboundCallTaskService outboundCallTaskService;

    @PostMapping
    public Result<OutboundCallTaskDO> create(@RequestBody OutboundCallTaskDO entity) {
        outboundCallTaskService.save(entity);
        return Result.success(entity);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outboundCallTaskService.removeById(id);
        return Result.success();
    }

    @PutMapping
    public Result<OutboundCallTaskDO> update(@RequestBody OutboundCallTaskDO entity) {
        outboundCallTaskService.updateById(entity);
        return Result.success(entity);
    }

    @GetMapping("/{id}")
    public Result<OutboundCallTaskDO> getById(@PathVariable Long id) {
        return Result.success(outboundCallTaskService.getById(id));
    }

    @GetMapping("/query")
    public Result<OutboundCallTaskDO> getByInstanceAndCode(@RequestParam String instanceId,
                                                           @RequestParam String taskCode) {
        return Result.success(outboundCallTaskService.getByInstanceAndCode(instanceId, taskCode));
    }

    @GetMapping("/page")
    public Result<Page<OutboundCallTaskDO>> page(@RequestParam String instanceId,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(outboundCallTaskService.pageByInstance(instanceId, pageNum, pageSize));
    }

    @GetMapping("/processing")
    public Result<Page<OutboundCallTaskDO>> pageProcessing(@RequestParam(defaultValue = "1") int pageNum,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(outboundCallTaskService.pageProcessingTasks(pageNum, pageSize));
    }

    @PutMapping("/status")
    public Result<Boolean> updateStatus(@RequestParam String instanceId,
                                        @RequestParam String taskCode,
                                        @RequestParam Integer status) {
        return Result.success(outboundCallTaskService.updateStatus(instanceId, taskCode, status));
    }
}
