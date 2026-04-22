package com.actionow.agent.saa.hook;

import com.actionow.agent.interaction.AgentStreamBridge;
import com.actionow.agent.saa.interceptor.StatusEmittingInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 向 SSE 发 LLM 调用阶段 status 事件的 Hook。
 *
 * <p>挂接到 ReactAgent hooks 链末尾，使 {@link StatusEmittingInterceptor}
 * 成为最外层拦截器（最先开始、最后结束），覆盖整个 LLM 调用耗时。
 *
 * @author Actionow
 */
@RequiredArgsConstructor
public class StatusEmittingHook extends AgentHook {

    private final AgentStreamBridge streamBridge;

    @Override
    public String getName() {
        return "StatusEmittingHook";
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(new StatusEmittingInterceptor(streamBridge));
    }
}
