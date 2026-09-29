package com.example.tool.security.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验 {@code dm_} 令牌。由 {@code McpSecurityConfig} 手动 {@code new} 出来挂到
 * MCP 链私有的 {@code ProviderManager} 上，<b>刻意不加 {@code @Component}</b>：
 *
 * <p>一旦它成为容器里唯一的 {@code AuthenticationProvider} bean，
 * {@code InitializeAuthenticationProviderBeanManagerConfigurer} 会把它塞进全局
 * {@code AuthenticationManager}，随后 {@code InitializeUserDetailsBeanManagerConfigurer}
 * 见 {@code auth.isConfigured()} 为真就跳过，不再挂 {@code DaoAuthenticationProvider}
 * —— 结果是 {@code AuthController} 的账号密码登录直接 {@code ProviderNotFoundException}。
 */
@RequiredArgsConstructor
public class McpTokenAuthenticationProvider implements AuthenticationProvider {
    private final McpTokenService mcpTokenService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Object p = authentication.getPrincipal();
        if (!(p instanceof String raw) || raw.isBlank())
            throw new BadCredentialsException("MCP token is missing");

        McpTokenPrincipal t = mcpTokenService.findValid(raw, clientIp(authentication));   // 查库 + enabled + 过期 + 回写
        if (t == null) throw new BadCredentialsException("MCP token is invalid, disabled or expired");

        List<GrantedAuthority> grants = new ArrayList<>();
        grants.add(new SimpleGrantedAuthority("TIER:"  + t.tier()));
        grants.add(new SimpleGrantedAuthority("OWNER:" + t.ownerId()));   // McpScopeGuard 靠它判归属
        t.permissions().forEach(x -> grants.add(new SimpleGrantedAuthority("SCOPE:" + x.scope())));

        return new PreAuthenticatedAuthenticationToken(t.id(), null, grants);   // 3 参 = authenticated
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 调用方 IP 由 {@link McpBearerFilter} 通过 {@code Authentication#setDetails} 带进来，
     * 这样 provider 不必去碰 HttpServletRequest（也方便以后单测）。
     */
    private static String clientIp(Authentication authentication) {
        Object details = authentication.getDetails();
        return (details instanceof WebAuthenticationDetails web) ? web.getRemoteAddress() : null;
    }
}
