package com.platform.config;

import com.platform.interceptor.AgentAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册 Token 鉴权拦截器 + 跨域
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AgentAuthInterceptor agentAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 仅拦截 agent 调用的三类接口，管理后台 /instances 等不走此拦截器（可后续接入后台账号体系）
        registry.addInterceptor(agentAuthInterceptor)
                .addPathPatterns(
                        "/agent/register",
                        "/agent/heartbeat",
                        "/agent/deregister"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}