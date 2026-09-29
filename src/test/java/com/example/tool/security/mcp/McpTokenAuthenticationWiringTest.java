package com.example.tool.security.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef")
class McpTokenAuthenticationWiringTest {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * MCP 令牌校验器一旦被注册成 AuthenticationProvider bean，Spring Security 就会把它
     * 装进全局 AuthenticationManager，同时跳过基于 UserDetailsService 的
     * DaoAuthenticationProvider —— 账号密码登录随即 ProviderNotFoundException。
     * 这个测试锁死该行为（AuthController 走的就是全局 AuthenticationManager）。
     */
    @Test
    void globalAuthenticationManagerStillSupportsUsernamePasswordLogin() {
        ProviderManager providerManager = assertInstanceOf(ProviderManager.class, authenticationManager);
        boolean supported = providerManager.getProviders().stream()
                .anyMatch(provider -> provider.supports(UsernamePasswordAuthenticationToken.class));
        assertTrue(supported, "全局 AuthenticationManager 必须还能处理账号密码登录");
    }

    /**
     * McpBearerFilter 是 Filter bean 的话会被 ServletContextInitializerBeans 自动注册进
     * Servlet 容器，在安全链之外对所有请求再跑一遍；
     * McpTokenAuthenticationProvider 是 bean 的话会顶掉全局 DaoAuthenticationProvider。
     * 两者都必须由 McpSecurityConfig 手动 new，不进容器。
     */
    @Test
    void mcpAuthenticationComponentsAreNotSpringBeans() {
        assertEquals(0, applicationContext.getBeanNamesForType(McpBearerFilter.class).length,
                "McpBearerFilter 不能是 bean");
        assertEquals(0, applicationContext.getBeanNamesForType(McpTokenAuthenticationProvider.class).length,
                "McpTokenAuthenticationProvider 不能是 bean");
    }
}
