package com.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.common.BizException;
import com.platform.common.ResultCode;
import com.platform.entity.Token;
import com.platform.mapper.TokenMapper;
import com.platform.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final TokenMapper tokenMapper;

    @Override
    public Token create(String remark) {
        // 生成 32 位随机串作为 token（生产环境可换 UUID）
        String tokenValue;
        int attempts = 0;
        do {
            tokenValue = RandomUtil.randomString(32);
            attempts++;
            if (attempts > 5) {
                tokenValue = IdUtil.simpleUUID();
                break;
            }
        } while (tokenMapper.selectCount(
                new LambdaQueryWrapper<Token>().eq(Token::getToken, tokenValue)) > 0);

        Token t = new Token();
        t.setToken(tokenValue);
        t.setRemark(remark == null ? "" : remark);
        t.setEnabled(1);
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        tokenMapper.insert(t);
        return t;
    }

    @Override
    public List<Token> list() {
        return tokenMapper.selectList(
                new LambdaQueryWrapper<Token>().orderByDesc(Token::getCreateTime));
    }

    @Override
    public void revoke(Long id) {
        Token exist = tokenMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "Token 不存在");
        }
        Token upd = new Token();
        upd.setId(id);
        upd.setEnabled(0);
        upd.setUpdateTime(LocalDateTime.now());
        tokenMapper.updateById(upd);
    }

    @Override
    public void enable(Long id) {
        Token exist = tokenMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "Token 不存在");
        }
        Token upd = new Token();
        upd.setId(id);
        upd.setEnabled(1);
        upd.setUpdateTime(LocalDateTime.now());
        tokenMapper.updateById(upd);
    }

    @Override
    public void delete(Long id) {
        Token exist = tokenMapper.selectById(id);
        if (exist == null) {
            return;
        }
        tokenMapper.deleteById(id);
    }
}