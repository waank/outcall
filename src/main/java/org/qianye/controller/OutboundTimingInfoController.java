package org.qianye.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.qianye.common.Result;
import org.qianye.entity.OutboundTimingInfoDO;
import org.qianye.service.OutboundTimingInfoService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 外呼择时信息管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/outbound-timing-info")
public class OutboundTimingInfoController {
    @Resource
    private OutboundTimingInfoService outboundTimingInfoService;

    @PostMapping
    public Result<OutboundTimingInfoDO> create(@RequestBody OutboundTimingInfoDO entity) {
        outboundTimingInfoService.save(entity);
        return Result.success(entity);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outboundTimingInfoService.removeById(id);
        return Result.success();
    }

    @PutMapping
    public Result<OutboundTimingInfoDO> update(@RequestBody OutboundTimingInfoDO entity) {
        outboundTimingInfoService.updateById(entity);
        return Result.success(entity);
    }

    @GetMapping("/{id}")
    public Result<OutboundTimingInfoDO> getById(@PathVariable Long id) {
        return Result.success(outboundTimingInfoService.getById(id));
    }

    @GetMapping("/phone")
    public Result<OutboundTimingInfoDO> getByPhone(@RequestParam String phone) {
        return Result.success(outboundTimingInfoService.getByPhone(phone));
    }

    @GetMapping("/page")
    public Result<Page<OutboundTimingInfoDO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(outboundTimingInfoService.page(new Page<>(pageNum, pageSize)));
    }
}
