package com.example.tool.security.mcp;

import com.example.tool.security.mcp.request.CreateMcpTokenRequest;
import com.example.tool.security.mcp.response.CreateMcpTokenResponse;
import com.example.tool.security.mcp.response.FindMcpTokenResponse;
import com.example.tool.user.util.CustomUserDetails;
import jakarta.validation.constraints.NotNull;
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
    public CreateMcpTokenResponse createToken(@AuthenticationPrincipal @NotNull CustomUserDetails userDetails, @RequestBody CreateMcpTokenRequest createMcpTokenRequest) {
        return mcpTokenService.createMcpToken(createMcpTokenRequest, userDetails.getUserId());
    }

    @GetMapping
    public List<FindMcpTokenResponse> findAll(@AuthenticationPrincipal @NotNull CustomUserDetails userDetails) {
        return mcpTokenService.findAllMcpToken(userDetails.getUserId());
    }

    @DeleteMapping("/delete/{tokenId}")
    public void deleteToken(@AuthenticationPrincipal @NotNull CustomUserDetails userDetails, @PathVariable Integer tokenId) {
        mcpTokenService.deleteMcpToken(userDetails.getUserId(), tokenId);
    }

    @PatchMapping("/deprecated/{tokenId}")
    @ResponseStatus(HttpStatus.OK)
    public FindMcpTokenResponse deprecatedToken(@AuthenticationPrincipal @NotNull CustomUserDetails userDetails, @PathVariable Integer tokenId) {
        return mcpTokenService.deprecatedMcpToken(tokenId, userDetails.getUserId());
    }
}
