package com.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预授权凭证
 */
@Data
@TableName("t_token")
public class Token {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Token 值（UUID） */
    private String token;

    /** 备注（一般为现场/站点名） */
    private String remark;

    /** 1=启用, 0=吊销 */
    private Integer enabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}