package com.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent → /agent/deregister 请求体
 */
@Data
public class DeregisterRequest {

    @NotBlank(message = "instanceId 不能为空")
    private String instanceId;
}