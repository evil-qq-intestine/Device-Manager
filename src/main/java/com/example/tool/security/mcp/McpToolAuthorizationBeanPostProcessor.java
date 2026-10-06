package com.example.tool.security.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 把 {@link McpRequires} 注解变成真正的运行时拦截。
 *
 * <p><b>为什么不走 Spring AOP</b>：Spring AI 的注解扫描器
 * （{@code AbstractMcpToolProvider#doGetClassMethods}）用
 * {@code bean.getClass().getDeclaredMethods()} + {@code method.getAnnotation(McpTool.class)}
 * 发现工具；工具 Bean 一旦被 CGLIB 代理，方法上的注解会丢，工具直接不注册。
 * 所以这里不改 Bean，而是在 Spring AI 生成 {@code List<SyncToolSpecification>} 之后
 * 替换其 {@code callHandler}，在调用前插入一层校验。
 *
 * <p>两个职责合并在一个 BeanPostProcessor 里：
 * <ol>
 *   <li>扫描带 {@code @McpTool} + {@link McpRequires} 的方法，按工具名登记要求；</li>
 *   <li>包装工具 spec 列表的 callHandler，调用时按 {@code request.name()} 惰性查表并校验
 *       （惰性查表可避免 Bean 初始化顺序导致的漏登记）。</li>
 * </ol>
 *
 * <p>注册的工具名 key 与 {@code Tool.name()}/{@code CallToolRequest.name()} 一致：
 * 注解 name 为空时取方法名。
 */
@Slf4j
public class McpToolAuthorizationBeanPostProcessor implements BeanPostProcessor {

    private final Map<String, McpRequires> requirements = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof List<?> list && isToolSpecList(list)) {
            return guard(list);
        }
        register(bean);
        return bean;
    }

    private boolean isToolSpecList(List<?> list) {
        return !list.isEmpty() && list.get(0) instanceof SyncToolSpecification;
    }

    private List<SyncToolSpecification> guard(List<?> list) {
        List<SyncToolSpecification> guarded = new ArrayList<>(list.size());
        for (Object element : list) {
            guarded.add(guard((SyncToolSpecification) element));
        }
        return guarded;
    }

    private SyncToolSpecification guard(SyncToolSpecification spec) {
        BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> original = spec.callHandler();
        BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> guarded =
                (exchange, request) -> {
                    McpRequires requires = requirements.get(request.name());
                    if (requires != null) {
                        McpScopeGuard.enforce(requires);
                    }
                    return original.apply(exchange, request);
                };
        return SyncToolSpecification.builder()
                .tool(spec.tool())
                .callHandler(guarded)
                .build();
    }

    private void register(Object bean) {
        if (bean == null) {
            return;
        }
        for (Method method : AopUtils.getTargetClass(bean).getMethods()) {
            McpTool tool = method.getAnnotation(McpTool.class);
            if (tool == null) {
                continue;
            }
            McpRequires requires = method.getAnnotation(McpRequires.class);
            if (requires == null) {
                continue;
            }
            String name = StringUtils.hasText(tool.name()) ? tool.name() : method.getName();
            requirements.put(name, requires);
            log.info("MCP tool '{}' requires tier>={}, scopes={}",
                    name, requires.tier(), Arrays.toString(requires.scopes()));
        }
    }
}
