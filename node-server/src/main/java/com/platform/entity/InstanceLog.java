package com.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实例操作日志
 */
@Data
@TableName("t_instance_log")
public class InstanceLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String instanceId;

    /** register / heartbeat / deregister / bind / auto_offline / auto_deregister */
    private String action;

    private String detail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}