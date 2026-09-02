package com.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.common.Result;
import com.platform.entity.InstanceLog;
import com.platform.mapper.InstanceLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 实例操作日志查询
 */
@RestController
@RequestMapping("/instances/{instanceId}/logs")
@RequiredArgsConstructor
public class InstanceLogController {

    private final InstanceLogMapper instanceLogMapper;

    @GetMapping
    public Result<List<InstanceLog>> list(@PathVariable String instanceId,
                                          @RequestParam(defaultValue = "50") Integer limit) {
        Page<InstanceLog> p = Page.of(1, Math.min(limit, 500));
        LambdaQueryWrapper<InstanceLog> w = new LambdaQueryWrapper<>();
        w.eq(InstanceLog::getInstanceId, instanceId)
                .orderByDesc(InstanceLog::getCreateTime);
        return Result.ok(instanceLogMapper.selectPage(p, w).getRecords());
    }
}