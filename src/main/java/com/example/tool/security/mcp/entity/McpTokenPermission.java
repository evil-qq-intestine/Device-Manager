package com.example.tool.security.mcp.entity;

/**
 * 令牌可用的 capability。枚举名是 Java 侧的标识（存进子表），
 * {@link #scope()} 是对外的 scope 字符串（与文档 §3.2 清单、{@code SCOPE:*} authority 一致）。
 */
public enum McpTokenPermission {

    DEVICE_READ("device:read"),
    DEVICE_WAKE("device:wake"),
    DEVICE_SSH("device:ssh"),
    DEVICE_SHUTDOWN("device:shutdown"),
    SCRIPT_READ("script:read"),
    SCRIPT_WRITE("script:write"),
    SCRIPT_RUN("script:run"),
    VERSION_READ("version:read");

    private final String scope;

    McpTokenPermission(String scope) {
        this.scope = scope;
    }

    public String scope() {
        return scope;
    }
}
