package com.platform.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent → /agent/register 请求体
 */
@Data
public class RegisterRequest {

    /** 实例唯一 ID（agent 持久化，重启不变） */
    @NotBlank(message = "instanceId 不能为空")
    private String instanceId;

    /** 主机名 */
    @NotBlank(message = "name 不能为空")
    private String name;

    /** IP */
    private String ip;

    /** Node-RED 监听端口 */
    private Integer port;

    /** 操作系统 */
    private String platform;

    /** CPU 架构 */
    private String arch;

    /** Node 版本 */
    private String nodeVersion;

    /** Node-RED 版本 */
    private String nodeRedVersion;

    /** 本次启动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
}