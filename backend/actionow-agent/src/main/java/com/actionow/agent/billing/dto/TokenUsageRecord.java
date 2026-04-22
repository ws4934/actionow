package com.actionow.agent.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 使用记录
 * 用于记录单次 LLM 调用的 Token 消耗
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageRecord {

    /**
     * 计费会话ID
     */
    private String billingSessionId;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 输入 token 数
     */
    private Integer inputTokens;

    /**
     * 输出 token 数
     */
    private Integer outputTokens;

    /**
     * 思考 token 数（模型内部推理）
     */
    private Integer thoughtTokens;

    /**
     * 缓存 token 数（复用缓存）
     */
    private Integer cachedTokens;

    /**
     * 计算的费用（积分）
     */
    private Long cost;

    /**
     * 模型厂商
     */
    private String modelProvider;

    /**
     * 模型ID
     */
    private String modelId;
}
