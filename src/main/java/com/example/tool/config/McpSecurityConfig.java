package com.example.tool.config;

import com.example.tool.security.mcp.McpBearerFilter;
import com.example.tool.security.mcp.McpTokenAuthenticationProvider;
import com.example.tool.security.mcp.McpTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
public class McpSecurityConfig {

    /**
     * MCP 专用链：@Order(1) 必须排在 WebSecurityConfig 那条（没写 @Order → 默认最低优先级）之前。
     *
     * securityMatcher 是两条链唯一的物理隔离点 —— 没有它两条链都匹配所有请求，
     * FilterChainProxy 按 @Order 取第一个命中的就用，顺序一乱 /mcp 和 /api 会互相接管。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, McpTokenService mcpTokenService)
            throws Exception {

        // MCP 令牌校验器：手动 new、手动挂到这条链私有的 AuthenticationManager 上。
        //
        // 为什么不把它注册成 bean（@Component 或 @Bean）：
        //   只要容器里有【且仅有】一个 AuthenticationProvider bean，Spring Security 的
        //   InitializeAuthenticationProviderBeanManagerConfigurer 就会把它装进全局
        //   AuthenticationManager；接着 InitializeUserDetailsBeanManagerConfigurer 看到
        //   auth.isConfigured() 为真就直接 return，不再挂基于 UserDetailsService 的
        //   DaoAuthenticationProvider —— AuthController 的账号密码登录会
        //   ProviderNotFoundException。这里 0 个 provider bean，全局认证行为与加 MCP 之前完全一致。
        AuthenticationManager mcpAuthenticationManager =
                new ProviderManager(new McpTokenAuthenticationProvider(mcpTokenService));

        http
                .securityMatcher("/mcp/**")                                    // 物理隔离，只管 /mcp
                .csrf(csrf -> csrf.disable())                                  // JSON-RPC，不是浏览器表单
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // 链已被 securityMatcher 限定在 /mcp/**，所以 anyRequest() == 任何 /mcp 请求
                        // 必须已认证；没 token 时不放行，交给下面的 bearer401() 出 401
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(bearer401())                 // RFC 6750 标准 401
                        .accessDeniedHandler(McpSecurityConfig::deny403))       // 已认证但权限/资源被拒
                // filter 同样是 new 出来的（不是 bean），否则 Spring Boot 的
                // ServletContextInitializerBeans 会把它自动注册进 Servlet 容器，
                // 在安全链之外对「所有请求」再跑一遍（那时 SecurityContext 已被清空，纯属白查库）。
                //
                // 锚点为什么是 AuthorizationFilter：FilterOrderRegistration 里它是 4200，
                // 是链尾真实存在的一环，而读 SecurityContextHolder 判 .authenticated() 的就是它。
                // 我们的 filter 必须在它之前把 Bearer dm_xxx 认成 Authentication 放进去，否则永远 401。
                // 手写 OncePerRequestFilter 在 order 表里查不到，addFilter() 会抛
                // "does not have a registered order"，所以只能 addFilterBefore 手动锚。
                .addFilterBefore(new McpBearerFilter(mcpAuthenticationManager), AuthorizationFilter.class);

        return http.build();
    }

    /**
     * 标准 401。spec 要求带 WWW-Authenticate —— 这里先只发 realm，
     * 将来真上了 OAuth Authorization Server 再在这里加 resource_metadata=（没上之前加了
     * 只会让客户端顺着去拉、拉不到、报更怪的错）。
     */
    private static AuthenticationEntryPoint bearer401() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.AuthenticationException authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Bearer realm=\"mcp\", error=\"invalid_token\"");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32001,\"message\":\"unauthorized\"}}");
        };
    }

    /**
     * 框架级 403（注意：工具内部的 scope 拒绝走的是 McpScopeGuard，那条路
     * 由 Spring AI 包成 result.isError=true + HTTP 200，让 LLM 能读到原因，不是这里）。
     * RFC 6750 §3.1：403 也该带 WWW-Authenticate，error 换成 insufficient_scope。
     */
    private static void deny403(HttpServletRequest request, HttpServletResponse response,
                                AccessDeniedException accessDeniedException) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setHeader("WWW-Authenticate", "Bearer realm=\"mcp\", error=\"insufficient_scope\"");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32004,\"message\":\"forbidden\"}}");
    }
}
