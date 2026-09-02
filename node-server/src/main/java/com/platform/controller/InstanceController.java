package com.platform.controller;

import com.platform.common.Result;
import com.platform.entity.Instance;
import com.platform.service.InstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 实例管理
 *
 * <p>说明：本版本暂不接入账号登录（按 docs/auto-register.md 仅要求 Agent 接口完成）；
 * 管理后台接口直接暴露在内网，生产环境请在网关层做 IP 白名单或接入统一登录。
 */
@RestController
@RequestMapping("/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceService instanceService;

    /** 实例列表 */
    @GetMapping
    public Result<List<Instance>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bindStatus,
            @RequestParam(required = false) String keyword) {
        return Result.ok(instanceService.listInstances(status, bindStatus, keyword));
    }

    /** 实例详情 */
    @GetMapping("/{instanceId}")
    public Result<Instance> detail(@PathVariable String instanceId) {
        return Result.ok(instanceService.getInstance(instanceId));
    }

    /** 绑定（未绑定 → 已绑定） */
    @PutMapping("/{instanceId}/bind")
    public Result<Void> bind(@PathVariable String instanceId,
                             @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        instanceService.bind(instanceId, remark);
        return Result.ok();
    }

    /** 手动注销 */
    @DeleteMapping("/{instanceId}")
    public Result<Void> deregister(@PathVariable String instanceId,
                                   @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        instanceService.manualDeregister(instanceId, reason);
        return Result.ok();
    }

    /** 概览统计 */
    @GetMapping("/_stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(instanceService.statistics());
    }
}