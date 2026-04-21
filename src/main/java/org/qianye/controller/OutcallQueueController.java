package org.qianye.controller;

import lombok.extern.slf4j.Slf4j;
import org.qianye.service.OutcallQueueService;
import org.qianye.common.PageData;
import org.qianye.DTO.QueueDetailDTO;
import org.qianye.DTO.QueueDetailRequest;
import org.qianye.common.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 呼叫名单管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/outcall-queue")
public class OutcallQueueController {
    @Resource
    private OutcallQueueService outcallQueueService;

    @PostMapping
    public Result<QueueDetailDTO> create(@RequestBody QueueDetailDTO entity) {
        outcallQueueService.batchInsertQueue(Collections.singletonList(entity));
        return Result.success(entity);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outcallQueueService.removeById(id);
        return Result.success();
    }

    @PutMapping
    public Result<QueueDetailDTO> update(@RequestBody QueueDetailDTO entity) {
        outcallQueueService.updateByCode(entity);
        return Result.success(entity);
    }

    @GetMapping("/query")
    public Result<QueueDetailDTO> getByInstanceAndCode(@RequestParam String instanceId,
                                                       @RequestParam String queueCode,
                                                       @RequestParam String envId) {
        return Result.success(outcallQueueService.getByInstanceAndCode(instanceId, queueCode, envId));
    }

    @GetMapping("/page")
    public Result<PageData<List<QueueDetailDTO>>> page(@RequestParam String instanceId,
                                                       @RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        QueueDetailRequest queueDetailRequest = new QueueDetailRequest();
        queueDetailRequest.setInstanceId(instanceId);
        queueDetailRequest.setPageNum(pageNum);
        queueDetailRequest.setPageSize(pageSize);
        return Result.success(outcallQueueService.queryPage(queueDetailRequest));
    }

    @PutMapping("/status")
    public Result<Boolean> updateStatus(@RequestParam String instanceId,
                                        @RequestParam String queueCode,
                                        @RequestParam String envId,
                                        @RequestParam String status) {
        return Result.success(outcallQueueService.updateStatus(instanceId, queueCode, envId, status));
    }

    /**
     * 根据任务编码查询队列列表
     */
    @GetMapping("/by-task/{taskCode}")
    public Result<List<QueueDetailDTO>> getByTaskCode(@PathVariable String taskCode,
                                                      @RequestParam String instanceId,
                                                      @RequestParam(defaultValue = "test") String envId) {
        return Result.success(outcallQueueService.getByTaskCode(instanceId, taskCode, envId));
    }
}
