package com.actionow.agent.core.agent;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行响应
 * 适配 Google ADK SDK
 *
 * @author Actionow
 */
@Data
@Builder
public class AgentResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 最终回复内容
     */
    private String content;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 执行的工具调用信息列表
     */
    private List<ToolCallInfo> toolCalls;

    /**
     * 总迭代次数
     */
    private int iterations;

    /**
     * 执行耗时（毫秒）
     */
    private long elapsedMs;

    /**
     * Token 消耗统计
     */
    private TokenUsage tokenUsage;

    /**
     * 完成时间
     */
    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();

    /**
     * 扩展数据
     */
    private Map<String, Object> extras;

    /**
     * 获取总 Token 消耗（便捷方法）
     */
    public long getTotalTokens() {
        if (tokenUsage == null) {
            return 0;
        }
        return tokenUsage.getTotalTokens();
    }

    /**
     * 创建成功响应
     */
    public static AgentResponse success(String content, int iterations, long elapsedMs) {
        return AgentResponse.builder()
                .success(true)
                .content(content)
                .iterations(iterations)
                .elapsedMs(elapsedMs)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static AgentResponse failure(String errorMessage) {
        return AgentResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Token 使用统计
     */
    @Data
    @Builder
    public static class TokenUsage {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
    }

    /**
     * 工具调用信息
     */
    @Data
    @Builder
    public static class ToolCallInfo {
        private String toolName;
        private Map<String, Object> arguments;
        private boolean success;
        private Object result;
    }
}
