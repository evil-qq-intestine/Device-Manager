package com.example.tool.security.mcp.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * 签发令牌的响应。{@code token} 是明文 {@code dm_xxx}，**只在这一次返回**，
 * 库里只留 SHA-256 摘要，丢了就只能重新签发。
 *
 * <p>{@code permissions} 与 {@link FindMcpTokenResponse#getPermissions()} 一致，
 * 都是 scope 字符串（如 {@code device:read}），避免前端要处理两种形状。
 */
@Getter
@Setter
public class CreateMcpTokenResponse {

    private String token;

    private String name;

    private Set<String> permissions;

    public CreateMcpTokenResponse(String token, Set<String> permissions, String name) {
        this.token = token;
        this.permissions = permissions;
        this.name = name;
    }
}
