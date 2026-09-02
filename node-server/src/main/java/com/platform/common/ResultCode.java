package com.platform.common;

import lombok.Getter;

/**
 * 业务返回码
 *
 * <p>规范：
 *   0         成功
 *   1xxx      参数 / 业务校验失败
 *   2xxx      鉴权 / 凭证相关（Agent 收到应停止重试）
 *   4xxx      资源 / 状态相关（Agent 收到应触发重新注册）
 *   5xxx      系统异常
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),

    BAD_REQUEST(1001, "请求参数错误"),

    UNAUTHORIZED(2001, "凭证无效或已吊销"),
    TOKEN_DISABLED(2002, "Token 已吊销"),

    INSTANCE_NOT_FOUND(4001, "实例不存在"),
    INSTANCE_DEREGISTERED(4002, "实例已注销"),

    INTERNAL_ERROR(5000, "系统内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}