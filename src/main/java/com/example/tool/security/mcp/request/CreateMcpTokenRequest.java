package com.example.tool.security.mcp.request;

import com.example.tool.security.mcp.entity.McpToken;
import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.entity.McpTokenTier;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
public class CreateMcpTokenRequest {
    //
    private Integer id;
    private Integer ownerId;
    private McpTokenTier tier;
    private Set<McpTokenPermission> permissions;
    private String name;
    private Instant expiresAt;
    private String allowIp;

    public static McpToken form(CreateMcpTokenRequest createMcpTokenRequest) {
        McpToken mcpToken = new McpToken();
        //mcpToken.setId(createMcpTokenRequest.getOwnerId());
        mcpToken.setName(createMcpTokenRequest.getName());
        mcpToken.setExpiresAt(createMcpTokenRequest.getExpiresAt());
        mcpToken.setAllowedIp(createMcpTokenRequest.getAllowIp());
        mcpToken.setTier(createMcpTokenRequest.getTier());
        mcpToken.setMcpTokenPermission(createMcpTokenRequest.getPermissions());
        return mcpToken;
    }
}
