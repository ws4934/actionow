package com.actionow.agent.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计费使用明细响应
 * 包含某个计费会话的详细使用记录
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingUsageResponse {

    /**
     * 计费会话 ID
     */
    private String billingSessionId;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 模型厂商
     */
    private String modelProvider;

    /**
     * 模型 ID
     */
    private String modelId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 总输入 Token 数
     */
    private Long totalInputTokens;

    /**
     * 总输出 Token 数
     */
    private Long totalOutputTokens;

    /**
     * LLM 调用费用（积分）
     */
    private Long llmCost;

    /**
     * AI 工具调用次数
     */
    private Integer aiToolCalls;

    /**
     * AI 工具调用费用（积分）
     */
    private Long aiToolCost;

    /**
     * 总费用（积分）
     */
    private Long totalCost;

    /**
     * 消息级别的使用记录
     */
    private List<TokenUsageRecord> messageUsages;

    /**
     * 会话开始时间
     */
    private LocalDateTime startTime;

    /**
     * 会话结束时间
     */
    private LocalDateTime endTime;

    /**
     * 会话状态
     */
    private String status;

    /**
     * 定价快照
     */
    private PricingSnapshot pricingSnapshot;
}
