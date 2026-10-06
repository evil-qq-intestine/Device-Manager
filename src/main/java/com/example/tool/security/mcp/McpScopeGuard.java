package com.example.tool.security.mcp;

import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.entity.McpTokenTier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/**
 * 工具侧鉴权。Spring AI 的 {@code tools/list} / {@code tools/call} 都不查权限，
 * 只有本类拦得住，所以每个 {@code @McpTool} 方法开头都要调一次
 * {@link #require(McpTokenPermission)}。
 *
 * <p>authority 由 {@link McpTokenAuthenticationProvider} 写入，三种前缀：
 * {@code TIER:EXTERNAL|TRUSTED}、{@code OWNER:<userId>}、{@code SCOPE:<scope 字符串>}。
 *
 * <p>写成静态工具类：读的是 {@code SecurityContextHolder} 这个 ThreadLocal，
 * 和 {@code SecurityContextHolder} 本身一样没有实例状态，不需要 Bean。
 */
public final class McpScopeGuard {

    private static final String OWNER_PREFIX = "OWNER:";
    private static final String TIER_PREFIX = "TIER:";
    private static final String SCOPE_PREFIX = "SCOPE:";

    private McpScopeGuard() {
    }

    /** 当前令牌归属的用户 id；无认证信息时返回 null。 */
    public static Integer ownerId() {
        String value = authorityValue(OWNER_PREFIX);
        return (value == null) ? null : Integer.valueOf(value);
    }

    /** 当前令牌等级（{@code EXTERNAL} / {@code TRUSTED}）；无认证信息时返回 null。 */
    public static String tier() {
        return authorityValue(TIER_PREFIX);
    }

    /**
     * 权限不足就抛 {@link McpAccessDeniedException}（LLM 能读到 message）。
     * 用枚举重载而不是裸字符串，避免 scope 拼错。
     */
    public static void require(McpTokenPermission permission) {
        require(permission.scope());
    }

    public static void require(String scope) {
        if (!hasScope(scope)) {
            throw new McpAccessDeniedException("当前令牌没有执行该操作所需的权限：" + scope);
        }
    }

    public static boolean hasScope(String scope) {
        return hasAuthority(SCOPE_PREFIX + scope);
    }

    /**
     * 执行 {@link McpRequires} 声明的校验：先比等级，再逐个查 scope。
     * 由 {@link McpToolAuthorizationBeanPostProcessor} 在工具调用前自动调用，
     * 工具方法里通常无需再手写。
     */
    public static void enforce(McpRequires requires) {
        if (requires == null) {
            return;
        }
        requireTier(requires.tier());
        for (McpTokenPermission permission : requires.scopes()) {
            require(permission);
        }
    }

    /**
     * 要求当前令牌等级不低于 {@code minimum}（{@code EXTERNAL < TRUSTED}）。
     * 无认证信息时同样拒绝——缺少上下文不能被当成「任意等级都放行」。
     */
    public static void requireTier(McpTokenTier minimum) {
        if (minimum == null) {
            return;
        }
        String value = tier();
        if (value == null) {
            throw new McpAccessDeniedException("当前请求没有 MCP 令牌认证信息，无法执行受保护的操作");
        }
        McpTokenTier current;
        try {
            current = McpTokenTier.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new McpAccessDeniedException("未知的令牌等级：" + value);
        }
        if (rank(current) < rank(minimum)) {
            throw new McpAccessDeniedException("当前令牌等级 " + current + " 权限不足，需要 " + minimum);
        }
    }

    /**
     * 当前令牌 id，用于写操作审计；来自 {@code McpTokenAuthenticationProvider} 写入的
     * principal（令牌主键）。无认证信息时返回 null。
     */
    public static Integer tokenId() {
        Authentication authentication = authentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return (principal instanceof Integer id) ? id : null;
    }

    /** EXTERNAL=0 < TRUSTED=1。 */
    private static int rank(McpTokenTier tier) {
        return tier == McpTokenTier.TRUSTED ? 1 : 0;
    }

    private static boolean hasAuthority(String authority) {
        Authentication authentication = authentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (Objects.equals(granted.getAuthority(), authority)) {
                return true;
            }
        }
        return false;
    }

    private static String authorityValue(String prefix) {
        Authentication authentication = authentication();
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            String authority = granted.getAuthority();
            if (authority != null && authority.startsWith(prefix)) {
                return authority.substring(prefix.length());
            }
        }
        return null;
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication;
    }
}
