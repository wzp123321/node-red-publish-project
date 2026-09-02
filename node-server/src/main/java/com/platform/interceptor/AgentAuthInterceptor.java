package com.platform.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.common.BizException;
import com.platform.common.ResultCode;
import com.platform.entity.Token;
import com.platform.mapper.TokenMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Agent 调用接口的 Token 鉴权
 *
 * <p>解析 Authorization: Bearer &lt;token&gt;，校验 t_token 表中是否存在且启用。
 * 校验通过后将 token 放入 request attribute，供 Controller 记录日志使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_TOKEN = "agent.token";
    public static final String ATTR_TOKEN_RECORD = "agent.token.record";

    private final TokenMapper tokenMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少 Authorization 头");
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Token 为空");
        }

        Token record = tokenMapper.selectOne(
                new LambdaQueryWrapper<Token>().eq(Token::getToken, token).last("LIMIT 1"));
        if (record == null) {
            log.warn("[agent-auth] token 不存在: {}", token);
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (record.getEnabled() == null || record.getEnabled() != 1) {
            log.warn("[agent-auth] token 已吊销: {}", token);
            throw new BizException(ResultCode.TOKEN_DISABLED);
        }

        request.setAttribute(ATTR_TOKEN, token);
        request.setAttribute(ATTR_TOKEN_RECORD, record);
        return true;
    }
}