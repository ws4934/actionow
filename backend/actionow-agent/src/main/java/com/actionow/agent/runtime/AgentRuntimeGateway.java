package com.actionow.agent.runtime;

import com.actionow.agent.core.agent.AgentStreamEvent;
import reactor.core.publisher.Flux;

/**
 * Agent 纯执行网关。
 */
public interface AgentRuntimeGateway {

    /**
     * 同步执行并返回完整轨迹。
     */
    ExecutionTranscript execute(ExecutionRequest request);

    /**
     * 流式执行并返回增量事件。
     */
    Flux<AgentStreamEvent> executeStream(ExecutionRequest request);
}
