package com.platform.controller;

import com.platform.common.Result;
import com.platform.dto.DeregisterRequest;
import com.platform.dto.HeartbeatRequest;
import com.platform.dto.RegisterRequest;
import com.platform.entity.Instance;
import com.platform.service.InstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 调用接口
 *
 * <p>路径前缀 /api/v1（由 application.yml 的 server.servlet.context-path 决定），
 * 完整路径示例：POST /api/v1/agent/register
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final InstanceService instanceService;

    /**
     * 注册 / 重新注册（幂等）
     * <p>请求头：Authorization: Bearer &lt;AGENT_TOKEN&gt;
     * <p>成功：HTTP 200 + code=0
     * <p>凭证无效：HTTP 401
     */
    @PostMapping("/register")
    public Result<Instance> register(@Valid @RequestBody RegisterRequest req) {
        Instance ins = instanceService.register(req);
        return Result.ok(ins);
    }

    /**
     * 心跳
     * <p>实例不存在：HTTP 404 + code=4001（Agent 会自动重新注册）
     * <p>实例已注销：HTTP 410 + code=4002（Agent 会自动重新注册）
     */
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
        instanceService.heartbeat(req);
        return Result.ok();
    }

    /**
     * 主动注销（Agent 退出时尽力调用）
     */
    @PostMapping("/deregister")
    public Result<Void> deregister(@Valid @RequestBody DeregisterRequest req) {
        instanceService.deregister(req);
        return Result.ok();
    }
}