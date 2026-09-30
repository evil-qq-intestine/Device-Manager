package com.example.tool.security.mcp.request;

import com.example.tool.security.mcp.entity.McpToken;
import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.entity.McpTokenTier;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreateMcpTokenRequest {
    private Integer id;
    private Integer ownerId;
    private McpTokenTier tier;
    private Set<McpTokenPermission> permissions;

//    public static McpToken form(CreateMcpTokenRequest createMcpTokenRequest) {
//        McpToken mcpToken = new McpToken();
//        mcpToken.setId(createMcpTokenRequest.getOwnerId());
//        mcpToken.setTier(createMcpTokenRequest.getTier());
//        mcpToken.setMcpTokenPermission(createMcpTokenRequest.getPermissions());
//        return mcpToken;
//    }
}
