package org.qianye.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.qianye.common.Result;
import org.qianye.entity.OutcallQueueGroupDO;
import org.qianye.service.OutcallQueueGroupService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 呼叫名单队列组管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/outcall-queue-group")
public class OutcallQueueGroupController {
    @Resource
    private OutcallQueueGroupService outcallQueueGroupService;

    @PostMapping
    public Result<OutcallQueueGroupDO> create(@RequestBody OutcallQueueGroupDO entity) {
        outcallQueueGroupService.save(entity);
        return Result.success(entity);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outcallQueueGroupService.removeById(id);
        return Result.success();
    }

    @PutMapping
    public Result<OutcallQueueGroupDO> update(@RequestBody OutcallQueueGroupDO entity) {
        outcallQueueGroupService.updateById(entity);
        return Result.success(entity);
    }

    @GetMapping("/{id}")
    public Result<OutcallQueueGroupDO> getById(@PathVariable Long id) {
        return Result.success(outcallQueueGroupService.getById(id));
    }

    @GetMapping("/query")
    public Result<OutcallQueueGroupDO> getByInstanceAndCode(@RequestParam String instanceId,
                                                            @RequestParam String envId,
                                                            @RequestParam String groupCode) {
        return Result.success(outcallQueueGroupService.getByInstanceAndCode(instanceId, envId, groupCode));
    }

    @GetMapping("/page")
    public Result<Page<OutcallQueueGroupDO>> page(@RequestParam String instanceId,
                                                  @RequestParam String taskCode,
                                                  @RequestParam String envId,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(outcallQueueGroupService.pageByTask(instanceId, taskCode, envId, pageNum, pageSize));
    }

    @PutMapping("/status")
    public Result<Boolean> updateStatus(@RequestParam String instanceId,
                                        @RequestParam String envId,
                                        @RequestParam String groupCode,
                                        @RequestParam String status) {
        return Result.success(outcallQueueGroupService.updateStatus(instanceId, envId, groupCode, status));
    }
}
