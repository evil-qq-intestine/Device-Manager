package com.example.tool.security.mcp;

/**
 * MCP 工具内部的鉴权失败（scope 不足、越权访问他人资源）。
 *
 * <p>故意继承 {@link RuntimeException} 而不是 Spring Security 的
 * {@code AccessDeniedException}：工具方法里抛出的普通 {@code Exception} 会被
 * Spring AI 的 {@code AbstractSyncMcpToolMethodCallback#createSyncErrorResult} 包成
 * {@code CallToolResult.isError=true} + HTTP 200，message 原样交给 LLM 让它自我纠正；
 * 若抛的是 {@code Error} 才会冒泡成 JSON-RPC error。
 *
 * <p><b>message 必须非空</b>：Spring AI 拼的是
 * {@code e.getMessage() + 换行 + rootCause.getMessage()}，null 会让 LLM 看到字面量 "null"。
 */
public class McpAccessDeniedException extends RuntimeException {

    public McpAccessDeniedException(String message) {
        super(message);
    }
}
