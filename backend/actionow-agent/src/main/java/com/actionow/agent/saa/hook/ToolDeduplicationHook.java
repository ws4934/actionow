package com.actionow.agent.saa.hook;

import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.actionow.agent.saa.interceptor.ToolDeduplicationInterceptor;

import java.util.List;

/**
 * 工具去重 Hook
 *
 * <p>SkillsInterceptor 每轮 ReAct 循环会重复追加已加载 Skill 的工具，
 * 导致 dynamicToolCallbacks 中出现同名工具。本 Hook 提供一个
 * {@link ToolDeduplicationInterceptor}，在 SkillsInterceptor 之后运行，
 * 按工具名去重后再传递给后续拦截器 / LLM。
 *
 * <p>必须在 hooks 链中排在 SkillsAgentHook <b>之后</b>，以保证执行顺序。
 *
 * @author Actionow
 */
public class ToolDeduplicationHook extends AgentHook {

    @Override
    public String getName() {
        return "ToolDeduplicationHook";
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(new ToolDeduplicationInterceptor());
    }
}
