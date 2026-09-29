package com.example.tool.security.mcp;

import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.entity.McpTokenTier;

import java.util.Set;

public record McpTokenPrincipal(Integer id, Integer ownerId, McpTokenTier tier, Set<McpTokenPermission> permissions) {}
