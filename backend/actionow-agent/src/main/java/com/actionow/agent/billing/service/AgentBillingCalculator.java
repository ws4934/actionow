package com.actionow.agent.billing.service;

import com.actionow.agent.billing.dto.LlmBillingRuleResponse;
import com.actionow.agent.billing.dto.TokenUsageRecord;
import com.actionow.agent.config.AgentRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 计费计算器
 * 根据计费规则计算 Token 消费的积分
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentBillingCalculator {

    private final LlmBillingRuleService billingRuleService;
    private final AgentRuntimeConfigService agentRuntimeConfig;

    /**
     * 每千 Token 的单位
     */
    private static final BigDecimal TOKENS_PER_UNIT = new BigDecimal("1000");

    /**
     * 计算 Token 消费的积分（使用 llmProviderId 查找规则）
     *
     * @param llmProviderId LLM Provider ID
     * @param inputTokens   输入 Token 数
     * @param outputTokens  输出 Token 数
     * @param thoughtTokens 思考 Token 数（按输出 Token 价格计费）
     * @return 消耗的积分（长整型，便于数据库存储）
     */
    public long calculateTokenCost(String llmProviderId,
                                   int inputTokens, int outputTokens, int thoughtTokens) {
        Optional<LlmBillingRuleResponse> ruleOpt = billingRuleService.getEffectiveRule(llmProviderId);
        if (ruleOpt.isEmpty()) {
            log.warn("未找到计费规则，使用默认价格: llmProviderId={}", llmProviderId);
            return calculateWithPrices(inputTokens, outputTokens, thoughtTokens,
                    new BigDecimal(agentRuntimeConfig.getBillingDefaultInputPrice()),
                    new BigDecimal(agentRuntimeConfig.getBillingDefaultOutputPrice()));
        }

        LlmBillingRuleResponse rule = ruleOpt.get();
        return calculateWithPrices(inputTokens, outputTokens, thoughtTokens,
                rule.getInputPrice(), rule.getOutputPrice());
    }

    /**
     * 使用定价快照计算 Token 消费的积分
     *
     * @param pricingSnapshot 定价快照
     * @param inputTokens     输入 Token 数
     * @param outputTokens    输出 Token 数
     * @param thoughtTokens   思考 Token 数
     * @return 消耗的积分
     */
    public long calculateTokenCostWithSnapshot(Map<String, Object> pricingSnapshot,
                                               int inputTokens, int outputTokens, int thoughtTokens) {
        BigDecimal defaultInputPrice = new BigDecimal(agentRuntimeConfig.getBillingDefaultInputPrice());
        BigDecimal defaultOutputPrice = new BigDecimal(agentRuntimeConfig.getBillingDefaultOutputPrice());
        BigDecimal inputPrice = getPriceFromSnapshot(pricingSnapshot, "inputPrice", defaultInputPrice);
        BigDecimal outputPrice = getPriceFromSnapshot(pricingSnapshot, "outputPrice", defaultOutputPrice);

        return calculateWithPrices(inputTokens, outputTokens, thoughtTokens, inputPrice, outputPrice);
    }

    /**
     * 从定价快照中获取价格
     */
    private BigDecimal getPriceFromSnapshot(Map<String, Object> snapshot, String key, BigDecimal defaultValue) {
        if (snapshot == null || !snapshot.containsKey(key)) {
            return defaultValue;
        }
        Object value = snapshot.get(key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            // 通过 toString() 转换，避免 double 精度损失（如 BigDecimal.valueOf(0.1d) 可能产生误差）
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 根据价格计算消费积分
     * 思考 Token 按输出 Token 价格计费
     */
    private long calculateWithPrices(int inputTokens, int outputTokens, int thoughtTokens,
                                     BigDecimal inputPrice, BigDecimal outputPrice) {
        // 输入费用 = (inputTokens / 1000) * inputPrice
        BigDecimal inputCost = new BigDecimal(inputTokens)
                .divide(TOKENS_PER_UNIT, 6, RoundingMode.HALF_UP)
                .multiply(inputPrice);

        // 输出费用 = (outputTokens / 1000) * outputPrice
        BigDecimal outputCost = new BigDecimal(outputTokens)
                .divide(TOKENS_PER_UNIT, 6, RoundingMode.HALF_UP)
                .multiply(outputPrice);

        // 思考费用 = (thoughtTokens / 1000) * outputPrice（思考按输出价格计费）
        BigDecimal thoughtCost = new BigDecimal(thoughtTokens)
                .divide(TOKENS_PER_UNIT, 6, RoundingMode.HALF_UP)
                .multiply(outputPrice);

        // 总费用，向上取整
        BigDecimal totalCost = inputCost.add(outputCost).add(thoughtCost)
                .setScale(0, RoundingMode.CEILING);

        return totalCost.longValue();
    }

    /**
     * 计算 Token 消费并生成记录（使用定价快照）
     *
     * @param billingSessionId 计费会话 ID
     * @param messageId        消息 ID
     * @param modelProvider    模型厂商
     * @param modelId          模型 ID
     * @param pricingSnapshot  定价快照
     * @param inputTokens      输入 Token 数
     * @param outputTokens     输出 Token 数
     * @param thoughtTokens    思考 Token 数
     * @return Token 使用记录
     */
    public TokenUsageRecord calculateAndRecord(String billingSessionId, String messageId,
                                                String modelProvider, String modelId,
                                                Map<String, Object> pricingSnapshot,
                                                int inputTokens, int outputTokens, int thoughtTokens) {
        long cost = calculateTokenCostWithSnapshot(pricingSnapshot, inputTokens, outputTokens, thoughtTokens);

        return TokenUsageRecord.builder()
                .billingSessionId(billingSessionId)
                .messageId(messageId)
                .modelProvider(modelProvider)
                .modelId(modelId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .thoughtTokens(thoughtTokens)
                .cost(cost)
                .build();
    }

    /**
     * 估算会话预冻结金额
     * 基于预估的 Token 消耗量计算需要冻结的积分
     *
     * @param llmProviderId        LLM Provider ID
     * @param estimatedInputTokens 预估输入 Token 数
     * @param estimatedOutputTokens 预估输出 Token 数
     * @return 预冻结金额
     */
    public long estimateFreezeAmount(String llmProviderId,
                                     int estimatedInputTokens, int estimatedOutputTokens) {
        long estimatedCost = calculateTokenCost(llmProviderId,
                estimatedInputTokens, estimatedOutputTokens, 0);

        // 增加 20% 缓冲
        return (long) (estimatedCost * 1.2);
    }

    /**
     * 获取默认的预冻结金额
     * 用于无法估算时的默认值
     *
     * @return 默认预冻结金额
     */
    public long getDefaultFreezeAmount() {
        return agentRuntimeConfig.getBillingDefaultFreezeAmount();
    }
}
