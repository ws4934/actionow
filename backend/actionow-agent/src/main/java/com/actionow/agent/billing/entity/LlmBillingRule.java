package com.actionow.agent.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 计费规则实体
 * 关联 LlmProvider，配置 Token 计费价格
 * 存储在 public schema 中
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_llm_billing_rule", autoResultMap = true)
public class LlmBillingRule extends BaseEntity {

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
     * 生效结束时间（NULL 表示无限期）
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
     * 优先级（同 Provider 多规则时的优先级）
     */
    private Integer priority;

    /**
     * 描述
     */
    private String description;
}
