package com.platform.service;

import com.platform.dto.DeregisterRequest;
import com.platform.dto.HeartbeatRequest;
import com.platform.dto.RegisterRequest;
import com.platform.entity.Instance;

import java.util.List;
import java.util.Map;

public interface InstanceService {

    /**
     * Agent 注册 / 重新注册（幂等）
     */
    Instance register(RegisterRequest req);

    /**
     * Agent 心跳
     */
    void heartbeat(HeartbeatRequest req);

    /**
     * Agent 主动注销
     */
    void deregister(DeregisterRequest req);

    /**
     * 管理后台 - 实例列表（支持状态/绑定过滤）
     */
    List<Instance> listInstances(String status, String bindStatus, String keyword);

    /**
     * 管理后台 - 实例详情
     */
    Instance getInstance(String instanceId);

    /**
     * 管理后台 - 绑定实例（未绑定 → 已绑定）
     */
    void bind(String instanceId, String remark);

    /**
     * 管理后台 - 手动注销
     */
    void manualDeregister(String instanceId, String reason);

    /**
     * 管理后台 - 概览统计
     */
    Map<String, Object> statistics();
}