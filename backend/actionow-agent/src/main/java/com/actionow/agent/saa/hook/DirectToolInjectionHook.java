package com.actionow.agent.saa.hook;

import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.actionow.agent.saa.interceptor.DirectToolInjectionInterceptor;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 将不属于任何 Skill 的 ToolCallback（如 Mission 控制工具）
 * 注入到 dynamicToolCallbacks，确保 AgentToolNode 可查找。
 *
 * <p>必须在 SkillsAgentHook 之后、ToolDeduplicationHook 之前运行。
 *
 * @author Actionow
 */
public class DirectToolInjectionHook extends AgentHook {

    private final List<ToolCallback> directTools;

    public DirectToolInjectionHook(List<ToolCallback> directTools) {
        this.directTools = directTools;
    }

    @Override
    public String getName() {
        return "DirectToolInjectionHook";
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(new DirectToolInjectionInterceptor(directTools));
    }
}
