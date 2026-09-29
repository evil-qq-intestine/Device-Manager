package com.example.tool.security.mcp.response;

import com.example.tool.security.mcp.entity.McpToken;
import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.entity.McpTokenTier;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 令牌对外视图。两个字段**永远不出现**：
 * <ul>
 *   <li>{@code tokenHash} —— 密钥的 SHA-256 摘要，等同于凭据，不该出外网；</li>
 *   <li>{@code jti} —— JWT 时代的遗留列，{@code dm_} 是不透明串、没有 jti claim，已废弃。</li>
 * </ul>
 *
 * <p>映射只走 {@link #from(McpToken)}
 */
@Getter
@Setter
public class FindMcpTokenResponse {

    private Integer id;

    private String name;

    private McpTokenTier tier;

    /** 对外的 scope 字符串（如 {@code device:read}），与文档 §3.2 清单、SCOPE:* authority 一致。 */
    private Set<String> permissions;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant lastUsedAt;

    private String allowedIp;

    private String usedIp;

    /** 注意不是 {@code isEnabled}：与实体列名解耦，也和 ScriptTaskResponse.enabled 保持一致。 */
    private Boolean enabled;

    public static FindMcpTokenResponse from(McpToken token) {
        FindMcpTokenResponse r = new FindMcpTokenResponse();
        r.id = token.getId();
        r.name = token.getName();
        r.tier = token.getTier();
        r.permissions = token.getMcpTokenPermission().stream()
                .map(McpTokenPermission::scope)
                .collect(Collectors.toSet());
        r.createdAt = token.getCreatedAt();
        r.expiresAt = token.getExpiresAt();
        r.lastUsedAt = token.getLastUsedAt();
        r.allowedIp = token.getAllowedIp();
        r.usedIp = token.getUsedIp();
        r.enabled = token.getIsEnabled();
        return r;
    }
}
