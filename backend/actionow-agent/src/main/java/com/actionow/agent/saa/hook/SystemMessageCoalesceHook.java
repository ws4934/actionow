package com.actionow.agent.saa.hook;

import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.actionow.agent.saa.interceptor.SystemMessageCoalesceInterceptor;

import java.util.List;

/**
 * SystemMessage 合并 Hook。
 *
 * <p>在 LLM 调用前把 {@link com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest}
 * 中的 systemMessage（来自 ReactAgent.systemPrompt）和 messages 列表中的所有 SystemMessage
 * 合并成一条，避免 Gemini 的 "Only one system message is allowed" 约束被触发。
 *
 * <p>必须在 hooks 链最后追加，确保所有其它 Hook / Interceptor（可能还会增加 SystemMessage）
 * 运行完毕后再做最终归一化。
 *
 * @author Actionow
 */
public class SystemMessageCoalesceHook extends AgentHook {

    @Override
    public String getName() {
        return "SystemMessageCoalesceHook";
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(new SystemMessageCoalesceInterceptor());
    }
}
