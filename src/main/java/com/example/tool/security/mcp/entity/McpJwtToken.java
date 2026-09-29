package com.example.tool.security.mcp.entity;

import com.example.tool.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "jwt_token")
public class McpJwtToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    private String name;

    /** SHA-256 十六进制摘要 = 64 字符。库里不存明文 dm_xxx。 */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "tier", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private McpTokenTier tier;

    /**
     * 一个令牌多个权限 → 子表 {@code api_token_permission}，一行一个值。
     * {@code joinColumns} 必须显式写，否则外键列会按「实体名_PK列名」自动生成
     * （{@code mcp_jwt_token_id}），实体一改名列名就跟着变。
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_token_permission",
            joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "token_permission", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<McpTokenPermission> mcpTokenPermission;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "allowed_ip", length = 45)
    private String allowedIp;

    @Column(name = "used_ip", length = 45)
    private String usedIp;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isEnabled == null) {
            isEnabled = true;
        }
    }
}