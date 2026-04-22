package com.actionow.agent.billing.dto;

import com.actionow.agent.billing.entity.AgentBillingSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 计费会话响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSessionResponse {

    /**
     * 会话ID
     */
    private String id;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 关联的 Agent 会话ID
     */
    private String conversationId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 模型厂商
     */
    private String modelProvider;

    /**
     * 模型ID
     */
    private String modelId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 冻结金额（积分）
     */
    private Long frozenAmount;

    /**
     * 总输入 token 数
     */
    private Long totalInputTokens;

    /**
     * 总输出 token 数
     */
    private Long totalOutputTokens;

    /**
     * 总思考 token 数（模型内部推理）
     */
    private Long totalThoughtTokens;

    /**
     * 总缓存 token 数（复用缓存）
     */
    private Long totalCachedTokens;

    /**
     * 总 token 数（便于前端展示）
     */
    private Long totalTokens;

    /**
     * LLM 对话消费（积分）
     */
    private Long llmCost;

    /**
     * AI 工具调用次数
     */
    private Integer aiToolCalls;

    /**
     * AI 工具消费（积分）
     */
    private Long aiToolCost;

    /**
     * 总消费（积分）
     */
    private Long totalCost;

    /**
     * 定价快照
     */
    private Map<String, Object> pricingSnapshot;

    /**
     * 状态
     */
    private String status;

    /**
     * 结算金额
     */
    private Long settledAmount;

    /**
     * 结算时间
     */
    private LocalDateTime settledAt;

    /**
     * 结算失败原因
     */
    private String settleError;

    /**
     * 最后活动时间
     */
    private LocalDateTime lastActivityAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换
     */
    public static BillingSessionResponse fromEntity(AgentBillingSession entity) {
        if (entity == null) {
            return null;
        }

        // 计算总 token 数
        long totalTokens = 0L;
        if (entity.getTotalInputTokens() != null) totalTokens += entity.getTotalInputTokens();
        if (entity.getTotalOutputTokens() != null) totalTokens += entity.getTotalOutputTokens();
        if (entity.getTotalThoughtTokens() != null) totalTokens += entity.getTotalThoughtTokens();

        return BillingSessionResponse.builder()
                .id(entity.getId())
                .workspaceId(entity.getWorkspaceId())
                .conversationId(entity.getConversationId())
                .userId(entity.getUserId())
                .modelProvider(entity.getModelProvider())
                .modelId(entity.getModelId())
                .modelName(entity.getModelName())
                .frozenAmount(entity.getFrozenAmount())
                .totalInputTokens(entity.getTotalInputTokens())
                .totalOutputTokens(entity.getTotalOutputTokens())
                .totalThoughtTokens(entity.getTotalThoughtTokens())
                .totalCachedTokens(entity.getTotalCachedTokens())
                .totalTokens(totalTokens)
                .llmCost(entity.getLlmCost())
                .aiToolCalls(entity.getAiToolCalls())
                .aiToolCost(entity.getAiToolCost())
                .totalCost(entity.getTotalCost())
                .pricingSnapshot(entity.getPricingSnapshot())
                .status(entity.getStatus())
                .settledAmount(entity.getSettledAmount())
                .settledAt(entity.getSettledAt())
                .settleError(entity.getSettleError())
                .lastActivityAt(entity.getLastActivityAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
