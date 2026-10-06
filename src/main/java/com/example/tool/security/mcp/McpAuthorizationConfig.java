package com.example.tool.security.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 MCP 工具鉴权用的 {@link McpToolAuthorizationBeanPostProcessor}。
 *
 * <p>必须用 {@code static @Bean}：BeanPostProcessor 要在普通 Bean 之前实例化，
 * 否则 {@code McpDevice} 等工具 Bean 可能在它注册前就被创建，导致漏登记。
 */
@Configuration
public class McpAuthorizationConfig {

    @Bean
    public static McpToolAuthorizationBeanPostProcessor mcpToolAuthorizationBeanPostProcessor() {
        return new McpToolAuthorizationBeanPostProcessor();
    }
}
