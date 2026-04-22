package com.actionow.agent.billing.dto;

import com.actionow.agent.billing.entity.LlmBillingRule;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 计费规则响应 DTO
 *
 * @author Actionow
 */
@Data
public class LlmBillingRuleResponse {

    private String id;

    /**
     * 关联的 LLM Provider ID
     */
    private String llmProviderId;

    /**
     * 输入 Token 价格（积分/1K tokens）
     */
    private BigDecimal inputPrice;

    /**
     * 输出 Token 价格（积分/1K tokens）
     */
    private BigDecimal outputPrice;

    /**
     * 生效开始时间
     */
    private LocalDateTime effectiveFrom;

    /**
     * 生效结束时间
     */
    private LocalDateTime effectiveTo;

    /**
     * 每分钟请求数限制
     */
    private Integer rateLimitRpm;

    /**
     * 每分钟 token 数限制
     */
    private Integer rateLimitTpm;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 描述
     */
    private String description;

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
    public static LlmBillingRuleResponse fromEntity(LlmBillingRule entity) {
        if (entity == null) {
            return null;
        }
        LlmBillingRuleResponse response = new LlmBillingRuleResponse();
        response.setId(entity.getId());
        response.setLlmProviderId(entity.getLlmProviderId());
        response.setInputPrice(entity.getInputPrice());
        response.setOutputPrice(entity.getOutputPrice());
        response.setEffectiveFrom(entity.getEffectiveFrom());
        response.setEffectiveTo(entity.getEffectiveTo());
        response.setRateLimitRpm(entity.getRateLimitRpm());
        response.setRateLimitTpm(entity.getRateLimitTpm());
        response.setEnabled(entity.getEnabled());
        response.setPriority(entity.getPriority());
        response.setDescription(entity.getDescription());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
