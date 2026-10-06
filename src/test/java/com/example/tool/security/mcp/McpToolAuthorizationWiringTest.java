package com.example.tool.security.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link McpToolAuthorizationBeanPostProcessor} 在真实上下文里被注册，
 * 且 {@code @McpRequires} 确实挂在 Spring AI 生成的工具 spec 上。
 */
@SpringBootTest(properties = "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef")
class McpToolAuthorizationWiringTest {

    @Autowired
    private ApplicationContext applicationContext;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registeredToolHandlerIsGuardedByAnnotation() {
        @SuppressWarnings("unchecked")
        List<SyncToolSpecification> specs = (List<SyncToolSpecification>) applicationContext.getBean("toolSpecs");

        SyncToolSpecification spec = specs.stream()
                .filter(s -> "get_user_device".equals(s.tool().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "get_user_device 未注册，当前工具：" + specs.stream().map(s -> s.tool().name()).toList()));

        assertTrue(applicationContext.containsBean("mcpToolAuthorizationBeanPostProcessor"),
                "鉴权 BeanPostProcessor 未注册");

        // 无认证 → 注解要求的 EXTERNAL 等级都拿不到 → 在调用原始工具前就被拒绝
        assertThrows(McpAccessDeniedException.class,
                () -> spec.callHandler().apply(null, new CallToolRequest("get_user_device", Map.of())));
    }
}
