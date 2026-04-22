package com.actionow.agent.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 计费规则请求 DTO
 *
 * @author Actionow
 */
@Data
public class LlmBillingRuleRequest {

    /**
     * 关联的 LLM Provider ID
     */
    @NotBlank(message = "LLM Provider ID 不能为空")
    private String llmProviderId;

    /**
     * 输入 Token 价格（积分/1K tokens）
     */
    @NotNull(message = "输入价格不能为空")
    private BigDecimal inputPrice;

    /**
     * 输出 Token 价格（积分/1K tokens）
     */
    @NotNull(message = "输出价格不能为空")
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
}
