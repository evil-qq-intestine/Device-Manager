package com.example.tool.security.mcp;

import com.example.tool.security.mcp.request.DeleteMcpTokenRequest;
import com.example.tool.security.mcp.response.CreateMcpTokenResponse;
import com.example.tool.security.mcp.response.FindMcpTokenResponse;
import com.example.tool.user.util.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp/token")
public class McpTokenController {

    private final McpTokenService mcpTokenService;
    public McpTokenController(McpTokenService tokenService) {
        this.mcpTokenService = tokenService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateMcpTokenResponse createToken(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody McpTokenPrincipal mcpTokenPrincipal) {
        return mcpTokenService.createMcpToken(mcpTokenPrincipal, userDetails.getUserId());
    }

    @GetMapping
    public List<FindMcpTokenResponse> findAll(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return mcpTokenService.findAllMcpToken(userDetails.getUserId());
    }

    @DeleteMapping
    public void deleteToken(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody DeleteMcpTokenRequest deleteMcpTokenRequest) {
        mcpTokenService.deleteMcpToken(userDetails.getUserId(), deleteMcpTokenRequest);
    }

    @PatchMapping
    @ResponseStatus(HttpStatus.OK)
    public FindMcpTokenResponse deprecatedToken(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody DeleteMcpTokenRequest deleteMcpTokenRequest) {
        return mcpTokenService.deprecatedMcpToken(deleteMcpTokenRequest, userDetails.getUserId());
    }
}
