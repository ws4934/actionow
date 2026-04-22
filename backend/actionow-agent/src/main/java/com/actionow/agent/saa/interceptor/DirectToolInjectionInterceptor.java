package com.actionow.agent.saa.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 直接工具注入拦截器。
 *
 * <p>将不属于任何 Skill 的工具回调（如 Mission 控制工具）注入到
 * {@code dynamicToolCallbacks}，确保 {@code AgentToolNode} 在执行
 * 工具调用时能查找到这些回调。
 *
 * <p>必须在 {@code SkillsInterceptor} 之后、{@code ToolDeduplicationInterceptor}
 * 之前运行。
 *
 * @author Actionow
 */
@Slf4j
public class DirectToolInjectionInterceptor extends ModelInterceptor {

    private final List<ToolCallback> directTools;

    public DirectToolInjectionInterceptor(List<ToolCallback> directTools) {
        this.directTools = directTools != null ? List.copyOf(directTools) : List.of();
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler next) {
        if (directTools.isEmpty()) {
            return next.call(request);
        }

        List<ToolCallback> dynamic = request.getDynamicToolCallbacks();
        Set<String> existingNames = new LinkedHashSet<>();
        if (dynamic != null) {
            for (ToolCallback cb : dynamic) {
                existingNames.add(cb.getToolDefinition().name());
            }
        }

        List<ToolCallback> toAdd = new ArrayList<>();
        for (ToolCallback tool : directTools) {
            if (existingNames.add(tool.getToolDefinition().name())) {
                toAdd.add(tool);
            }
        }

        if (toAdd.isEmpty()) {
            return next.call(request);
        }

        List<ToolCallback> merged = new ArrayList<>(dynamic != null ? dynamic : List.of());
        merged.addAll(toAdd);

        log.debug("Injected {} direct tool(s) into dynamicToolCallbacks", toAdd.size());

        ModelRequest injected = ModelRequest.builder(request)
                .dynamicToolCallbacks(merged)
                .build();
        return next.call(injected);
    }

    @Override
    public String getName() {
        return "DirectToolInjectionInterceptor";
    }
}
