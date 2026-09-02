package com.platform.task;

import com.platform.service.impl.InstanceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 心跳状态巡检
 *
 * <p>扫描周期由 {@code platform.scan-interval-seconds} 控制（默认 30s）。
 *   1. lastHeartbeat &lt; now − 2 × heartbeatInterval（即默认值 60s）→ 标记离线
 *   2. lastHeartbeat &lt; now − autoDeregisterHours（即 24h）→ 自动注销
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatCheckTask {

    private final InstanceServiceImpl instanceService;

    /**
     * 默认心跳间隔（秒）。Agent 默认 30s，平台判定离线阈值 = 60s。
     * 如需按实例实际心跳间隔判定，可后续在 t_instance 中加 heartbeat_interval 字段。
     */
    @Value("${platform.heartbeat-offline-multiplier:2}")
    private int offlineMultiplier;

    /** 自动注销阈值（小时） */
    @Value("${platform.auto-deregister-hours:24}")
    private int autoDeregisterHours;

    /** Agent 默认心跳间隔（秒）—— 与 deploy/agent/main.js 中的默认 30 保持一致 */
    private static final int DEFAULT_HEARTBEAT_INTERVAL_SEC = 30;

    @Scheduled(fixedDelayString = "${platform.scan-interval-seconds:30}000")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();

        // 离线阈值：now - 2 * 30s
        LocalDateTime offlineThreshold = now.minusSeconds(
                (long) offlineMultiplier * DEFAULT_HEARTBEAT_INTERVAL_SEC);
        instanceService.markOfflineIfTimeout(offlineThreshold);

        // 自动注销阈值：now - 24h
        LocalDateTime deregisterThreshold = now.minusHours(autoDeregisterHours);
        instanceService.autoDeregister(deregisterThreshold);
    }
}