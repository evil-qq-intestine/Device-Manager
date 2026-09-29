package com.example.tool.security.mcp;

import com.example.tool.security.mcp.response.CreateMcpTokenResponse;
import com.example.tool.user.util.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/mcp/token")
public class McpTokenController {

    private final McpTokenService mcpTokenService;
    public McpTokenController(McpTokenService tokenService) {
        this.mcpTokenService = tokenService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateMcpTokenResponse createToken(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody McpTokenPrincipal mcpTokenPrincipal) {
        return mcpTokenService.create(mcpTokenPrincipal, userDetails.getUserId());
    }
}
