package com.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Node-RED 实例
 */
@Data
@TableName("t_instance")
public class Instance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实例唯一 ID */
    private String instanceId;

    /** 主机名 / 自定义名称 */
    private String name;

    /** IP */
    private String ip;

    /** Node-RED 监听端口 */
    private Integer port;

    /** OS 平台 */
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

    /** 注册时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;

    /** 最后心跳时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatTime;

    /** online=在线, offline=离线, deregistered=已注销 */
    private String status;

    /** bound=已绑定, unbound=未绑定 */
    private String bindStatus;

    /** 绑定时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bindTime;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}