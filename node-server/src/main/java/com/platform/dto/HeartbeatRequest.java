package com.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent → /agent/heartbeat 请求体
 */
@Data
public class HeartbeatRequest {

    @NotBlank(message = "instanceId 不能为空")
    private String instanceId;
}