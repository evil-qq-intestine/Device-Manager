package com.example.tool.security.mcp;

import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.entity.McpTokenTier;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolAuthorizationBeanPostProcessorTest {

    public static class FakeTool {
        @McpTool(name = "fake_tool", description = "test")
        @McpRequires(tier = McpTokenTier.TRUSTED, scopes = McpTokenPermission.DEVICE_READ)
        public String fakeTool() {
            return "ok";
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesWhenNoAuthentication() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        var handler = wrappedHandler(invoked);

        assertThrows(McpAccessDeniedException.class, () -> handler.apply(null, request()));
        assertFalse(invoked.get(), "原始工具不应被调用");
    }

    @Test
    void deniesWhenTierTooLow() {
        authenticate("TIER:EXTERNAL", "OWNER:1", "SCOPE:device:read");
        AtomicBoolean invoked = new AtomicBoolean(false);
        var handler = wrappedHandler(invoked);

        assertThrows(McpAccessDeniedException.class, () -> handler.apply(null, request()));
        assertFalse(invoked.get(), "原始工具不应被调用");
    }

    @Test
    void deniesWhenScopeMissing() {
        authenticate("TIER:TRUSTED", "OWNER:1");
        AtomicBoolean invoked = new AtomicBoolean(false);
        var handler = wrappedHandler(invoked);

        assertThrows(McpAccessDeniedException.class, () -> handler.apply(null, request()));
        assertFalse(invoked.get(), "原始工具不应被调用");
    }

    @Test
    void allowsWhenTierAndScopeSatisfied() {
        authenticate("TIER:TRUSTED", "OWNER:1", "SCOPE:device:read");
        AtomicBoolean invoked = new AtomicBoolean(false);
        var handler = wrappedHandler(invoked);

        handler.apply(null, request());

        assertTrue(invoked.get(), "满足等级与 scope 后应调用原始工具");
    }

    private java.util.function.BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange,
            CallToolRequest, CallToolResult> wrappedHandler(AtomicBoolean invoked) {
        McpToolAuthorizationBeanPostProcessor bpp = new McpToolAuthorizationBeanPostProcessor();
        bpp.postProcessAfterInitialization(new FakeTool(), "fakeTool");

        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("fake_tool")
                .description("test")
                .inputSchema(Map.of())
                .build();
        SyncToolSpecification original = SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, call) -> {
                    invoked.set(true);
                    return CallToolResult.builder().build();
                })
                .build();

        @SuppressWarnings("unchecked")
        List<SyncToolSpecification> wrapped =
                (List<SyncToolSpecification>) bpp.postProcessAfterInitialization(List.of(original), "toolSpecs");
        return wrapped.get(0).callHandler();
    }

    private static CallToolRequest request() {
        return new CallToolRequest("fake_tool", Map.of());
    }

    private static void authenticate(String... authorities) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                1, null, java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
