package com.actionow.agent.runtime;

import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.actionow.agent.core.agent.AgentResponse;
import com.actionow.agent.core.agent.AgentStreamEvent;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 纯运行时执行结果。
 */
@Data
@Builder
public class ExecutionTranscript {

    private String finalText;

    private List<AgentResponse.ToolCallInfo> toolCalls;

    private TokenUsage usage;

    private String modelName;

    private List<AgentStreamEvent> rawEvents;

    private int iterations;

    /**
     * 本次执行使用的 Agent 实例（供 Mission 多步缓存复用）。
     */
    private SupervisorAgent agent;

    @Data
    @Builder
    public static class TokenUsage {
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
    }
}
