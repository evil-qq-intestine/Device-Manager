package com.example.tool.security.mcp;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 真实 Servlet 容器下验证「安全 filter 没有被 Spring Boot 重复注册」。
 *
 * <p>{@code ServletContextInitializerBeans} 会把容器里所有 {@code Filter} bean 包成
 * RegistrationBean 注册进 Servlet 容器，使 filter 在安全链之外对所有请求再跑一遍。
 * {@code McpBearerFilter} 与 {@code JwtAuthenticationFilter} 现在都不再是 bean，
 * {@code WebSecurityConfig} 里那条 setEnabled(false) 的 RegistrationBean 也已经不再需要，
 * 因此它们的 bean 名不该出现在 FilterRegistration 里。
 *
 * <p>必须用 {@code RANDOM_PORT}（而非默认 MOCK）—— MOCK 环境根本不启动容器，
 * ServletContextInitializer 不会被调用，这个 bug 也就测不出来。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef")
class ServletFilterRegistrationTest {

    @Autowired
    private ServletContext servletContext;

    @LocalServerPort
    private int port;

    @Test
    void mcpBearerFilterIsNotRegisteredWithServletContainer() {
        assertFalse(servletContext.getFilterRegistrations().containsKey("mcpBearerFilter"),
                "McpBearerFilter 不该被注册到 Servlet 容器：" + servletContext.getFilterRegistrations().keySet());
    }

    @Test
    void jwtAuthenticationFilterIsNotRegisteredWithServletContainer() {
        assertFalse(servletContext.getFilterRegistrations().containsKey("jwtAuthenticationFilter"),
                "JwtAuthenticationFilter 不该被注册到 Servlet 容器（否则 /mcp 会刷 JWT 解析失败告警）："
                        + servletContext.getFilterRegistrations().keySet());
    }
}
