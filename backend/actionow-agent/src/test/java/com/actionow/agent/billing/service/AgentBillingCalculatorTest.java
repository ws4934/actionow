package com.actionow.agent.billing.service;

import com.actionow.agent.billing.dto.LlmBillingRuleResponse;
import com.actionow.agent.billing.dto.TokenUsageRecord;
import com.actionow.agent.config.AgentRuntimeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AgentBillingCalculator 单元测试
 *
 * @author Actionow
 */
@ExtendWith(MockitoExtension.class)
class AgentBillingCalculatorTest {

    @Mock
    private LlmBillingRuleService billingRuleService;

    @Mock
    private AgentRuntimeConfigService agentRuntimeConfig;

    @InjectMocks
    private AgentBillingCalculator calculator;

    private static final String LLM_PROVIDER_ID = "provider-123";

    @Nested
    @DisplayName("calculateWithPrices 基础计算测试")
    class BasicCalculationTests {

        @Test
        @DisplayName("标准 Token 计算 - 输入 + 输出")
        void calculateTokenCost_standardTokens() {
            LlmBillingRuleResponse rule = new LlmBillingRuleResponse();
            rule.setInputPrice(new BigDecimal("0.5"));
            rule.setOutputPrice(new BigDecimal("1.5"));
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.of(rule));

            // 1000 input tokens * 0.5/1K + 500 output tokens * 1.5/1K = 0.5 + 0.75 = 1.25 → ceiling = 2
            long cost = calculator.calculateTokenCost(LLM_PROVIDER_ID, 1000, 500, 0);
            assertEquals(2, cost);
        }

        @Test
        @DisplayName("思考 Token 使用输出价格计费")
        void calculateTokenCost_thoughtTokensUseOutputPrice() {
            LlmBillingRuleResponse rule = new LlmBillingRuleResponse();
            rule.setInputPrice(new BigDecimal("0.5"));
            rule.setOutputPrice(new BigDecimal("1.5"));
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.of(rule));

            // 0 input + 0 output + 1000 thought tokens * 1.5/1K = 1.5 → ceiling = 2
            long cost = calculator.calculateTokenCost(LLM_PROVIDER_ID, 0, 0, 1000);
            assertEquals(2, cost);
        }

        @Test
        @DisplayName("零 Token 返回零费用")
        void calculateTokenCost_zeroTokens() {
            LlmBillingRuleResponse rule = new LlmBillingRuleResponse();
            rule.setInputPrice(new BigDecimal("0.5"));
            rule.setOutputPrice(new BigDecimal("1.5"));
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.of(rule));

            long cost = calculator.calculateTokenCost(LLM_PROVIDER_ID, 0, 0, 0);
            assertEquals(0, cost);
        }

        @Test
        @DisplayName("CEILING 舍入验证 - 微小费用向上取整")
        void calculateTokenCost_ceilingRounding() {
            LlmBillingRuleResponse rule = new LlmBillingRuleResponse();
            rule.setInputPrice(new BigDecimal("0.01"));
            rule.setOutputPrice(new BigDecimal("0.01"));
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.of(rule));

            // 1 input token * 0.01/1K = 0.00001 → ceiling = 1
            long cost = calculator.calculateTokenCost(LLM_PROVIDER_ID, 1, 0, 0);
            assertEquals(1, cost);
        }

        @Test
        @DisplayName("精确整数结果不多加 1")
        void calculateTokenCost_exactInteger() {
            LlmBillingRuleResponse rule = new LlmBillingRuleResponse();
            rule.setInputPrice(new BigDecimal("1.0"));
            rule.setOutputPrice(new BigDecimal("1.0"));
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.of(rule));

            // 1000 input tokens * 1.0/1K = 1.0 → ceiling = 1
            long cost = calculator.calculateTokenCost(LLM_PROVIDER_ID, 1000, 0, 0);
            assertEquals(1, cost);
        }

        @Test
        @DisplayName("大量 Token 计算")
        void calculateTokenCost_largeTokenCount() {
            LlmBillingRuleResponse rule = new LlmBillingRuleResponse();
            rule.setInputPrice(new BigDecimal("10"));
            rule.setOutputPrice(new BigDecimal("30"));
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.of(rule));

            // 100000 input * 10/1K + 50000 output * 30/1K = 1000 + 1500 = 2500
            long cost = calculator.calculateTokenCost(LLM_PROVIDER_ID, 100000, 50000, 0);
            assertEquals(2500, cost);
        }
    }

    @Nested
    @DisplayName("默认价格 fallback 测试")
    class DefaultPriceFallbackTests {

        @Test
        @DisplayName("无计费规则时使用默认价格")
        void calculateTokenCost_fallbackToDefaultPrice() {
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.empty());
            when(agentRuntimeConfig.getBillingDefaultInputPrice()).thenReturn("0.5");
            when(agentRuntimeConfig.getBillingDefaultOutputPrice()).thenReturn("1.5");

            // 1000 input * 0.5/1K + 1000 output * 1.5/1K = 0.5 + 1.5 = 2.0
            long cost = calculator.calculateTokenCost(LLM_PROVIDER_ID, 1000, 1000, 0);
            assertEquals(2, cost);
        }
    }

    @Nested
    @DisplayName("定价快照测试")
    class PricingSnapshotTests {

        @BeforeEach
        void setUp() {
            when(agentRuntimeConfig.getBillingDefaultInputPrice()).thenReturn("0.5");
            when(agentRuntimeConfig.getBillingDefaultOutputPrice()).thenReturn("1.5");
        }

        @Test
        @DisplayName("从快照中读取 BigDecimal 类型价格")
        void calculateWithSnapshot_bigDecimalPrices() {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("inputPrice", new BigDecimal("2.0"));
            snapshot.put("outputPrice", new BigDecimal("6.0"));

            // 1000 input * 2.0/1K + 1000 output * 6.0/1K = 2 + 6 = 8
            long cost = calculator.calculateTokenCostWithSnapshot(snapshot, 1000, 1000, 0);
            assertEquals(8, cost);
        }

        @Test
        @DisplayName("从快照中读取 Number (Double) 类型价格")
        void calculateWithSnapshot_doublePrices() {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("inputPrice", 2.0);
            snapshot.put("outputPrice", 6.0);

            long cost = calculator.calculateTokenCostWithSnapshot(snapshot, 1000, 1000, 0);
            assertEquals(8, cost);
        }

        @Test
        @DisplayName("从快照中读取 String 类型价格")
        void calculateWithSnapshot_stringPrices() {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("inputPrice", "2.0");
            snapshot.put("outputPrice", "6.0");

            long cost = calculator.calculateTokenCostWithSnapshot(snapshot, 1000, 1000, 0);
            assertEquals(8, cost);
        }

        @Test
        @DisplayName("快照为 null 时使用默认价格")
        void calculateWithSnapshot_nullSnapshot() {
            long cost = calculator.calculateTokenCostWithSnapshot(null, 1000, 1000, 0);
            // 1000 * 0.5/1K + 1000 * 1.5/1K = 0.5 + 1.5 = 2
            assertEquals(2, cost);
        }

        @Test
        @DisplayName("快照缺少价格字段时使用默认价格")
        void calculateWithSnapshot_missingKeys() {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("modelProvider", "OPENAI");

            long cost = calculator.calculateTokenCostWithSnapshot(snapshot, 1000, 1000, 0);
            assertEquals(2, cost);
        }
    }

    @Nested
    @DisplayName("calculateAndRecord 记录生成测试")
    class CalculateAndRecordTests {

        @BeforeEach
        void setUp() {
            when(agentRuntimeConfig.getBillingDefaultInputPrice()).thenReturn("1.0");
            when(agentRuntimeConfig.getBillingDefaultOutputPrice()).thenReturn("2.0");
        }

        @Test
        @DisplayName("生成完整的 TokenUsageRecord")
        void calculateAndRecord_generatesCompleteRecord() {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("inputPrice", "1.0");
            snapshot.put("outputPrice", "2.0");

            TokenUsageRecord record = calculator.calculateAndRecord(
                    "session-1", "msg-1", "OPENAI", "gpt-4",
                    snapshot, 500, 300, 100);

            assertEquals("session-1", record.getBillingSessionId());
            assertEquals("msg-1", record.getMessageId());
            assertEquals("OPENAI", record.getModelProvider());
            assertEquals("gpt-4", record.getModelId());
            assertEquals(500, record.getInputTokens());
            assertEquals(300, record.getOutputTokens());
            assertEquals(100, record.getThoughtTokens());
            // 500*1.0/1K + 300*2.0/1K + 100*2.0/1K = 0.5 + 0.6 + 0.2 = 1.3 → ceiling = 2
            assertEquals(2, record.getCost());
        }
    }

    @Nested
    @DisplayName("estimateFreezeAmount 预估冻结金额测试")
    class EstimateFreezeAmountTests {

        @Test
        @DisplayName("预估冻结金额包含 20% 缓冲")
        void estimateFreezeAmount_includes20PercentBuffer() {
            LlmBillingRuleResponse rule = new LlmBillingRuleResponse();
            rule.setInputPrice(new BigDecimal("1.0"));
            rule.setOutputPrice(new BigDecimal("1.0"));
            when(billingRuleService.getEffectiveRule(LLM_PROVIDER_ID)).thenReturn(Optional.of(rule));

            // 10000 input * 1.0/1K + 5000 output * 1.0/1K = 10 + 5 = 15 → *1.2 = 18
            long amount = calculator.estimateFreezeAmount(LLM_PROVIDER_ID, 10000, 5000);
            assertEquals(18, amount);
        }
    }

    @Nested
    @DisplayName("getDefaultFreezeAmount 测试")
    class DefaultFreezeAmountTests {

        @Test
        @DisplayName("返回配置中的默认冻结金额")
        void getDefaultFreezeAmount_returnsConfigValue() {
            when(agentRuntimeConfig.getBillingDefaultFreezeAmount()).thenReturn(100L);

            long amount = calculator.getDefaultFreezeAmount();
            assertEquals(100, amount);
        }
    }
}
