package com.example.tool.security.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把 {@code Authorization: Bearer dm_xxx} 换成 SecurityContext 里的 Authentication。
 *
 * <p>由 {@code McpSecurityConfig} 手动 {@code new} 出来并挂在 /mcp 链上，
 * <b>刻意不加 {@code @Component}</b>：Filter bean 会被 Spring Boot 的
 * {@code ServletContextInitializerBeans} 自动注册进 Servlet 容器，在安全链之外
 * 对「所有请求」再跑一遍。
 */
@RequiredArgsConstructor
@Slf4j
public class McpBearerFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AuthenticationManager authenticationManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String mcpAuthHeader = request.getHeader("Authorization");

        if(mcpAuthHeader == null || !mcpAuthHeader.startsWith(BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String raw = mcpAuthHeader.substring(BEARER.length()).trim();

        try {
            PreAuthenticatedAuthenticationToken unverified = new PreAuthenticatedAuthenticationToken(raw, null);
            // 把来源 IP 交给 provider 回写 usedIp；server.forward-headers-strategy=framework
            // 已让 ForwardedHeaderFilter 把 getRemoteAddr() 改写为 X-Forwarded-For 里的真实客户端
            unverified.setDetails(new WebAuthenticationDetails(request));
            Authentication authentication = authenticationManager.authenticate(unverified);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException e) {
            log.warn("MCP bearer token rejected: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
