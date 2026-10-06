package com.example.tool.security.mcp;

import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.entity.McpTokenTier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MCP 工具鉴权声明，标注在 {@code @McpTool} 方法上。
 *
 * <p>由 {@link McpToolAuthorizationBeanPostProcessor} 在运行时读取并强制校验，
 * 工具方法里不需要再手写 {@link McpScopeGuard#require}。
 *
 * <p>{@link #tier()} 是「最低令牌等级」，用于区分令牌等级：{@code EXTERNAL < TRUSTED}。
 * 标 {@code TRUSTED} 后 EXTERNAL 令牌调用会被拒绝；保持默认 {@code EXTERNAL}
 * 表示任意有效令牌都可调用。
 *
 * <p>{@link #scopes()} 是额外要求的 capability，等价于逐个调用
 * {@link McpScopeGuard#require(McpTokenPermission)}。
 *
 * <p>校验在工具回调线程上执行，失败抛 {@link McpAccessDeniedException}
 * （被 Spring AI 包成 {@code result.isError=true} + HTTP 200，message 交给 LLM）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpRequires {

    /** 允许调用该工具的最低令牌等级。 */
    McpTokenTier tier() default McpTokenTier.EXTERNAL;

    /** 调用该工具额外需要的 capability（scope）。 */
    McpTokenPermission[] scopes() default {};
}
