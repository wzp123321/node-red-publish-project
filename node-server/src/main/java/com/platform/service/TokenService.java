package com.platform.service;

import com.platform.entity.Token;

import java.util.List;

public interface TokenService {

    /**
     * 创建 Token（生成 UUID 凭证）
     */
    Token create(String remark);

    /**
     * 列出所有 Token
     */
    List<Token> list();

    /**
     * 吊销 Token
     */
    void revoke(Long id);

    /**
     * 重新启用 Token
     */
    void enable(Long id);

    /**
     * 删除 Token
     */
    void delete(Long id);
}