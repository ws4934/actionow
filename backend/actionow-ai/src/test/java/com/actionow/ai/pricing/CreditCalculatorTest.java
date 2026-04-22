package com.actionow.ai.pricing;

import com.actionow.ai.entity.ModelProvider;
import com.actionow.ai.plugin.groovy.GroovyScriptCache;
import groovy.lang.Script;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreditCalculator 单元测试
 */
class CreditCalculatorTest {

    private CreditCalculator calculator;
    private GroovyScriptCache scriptCache;

    @BeforeEach
    void setUp() {
        scriptCache = new GroovyScriptCache();
        calculator = new CreditCalculator(scriptCache);
    }

    @Nested
    @DisplayName("静态积分计算")
    class StaticCostTests {

        @Test
        @DisplayName("无规则无脚本时使用静态积分")
        void shouldUseStaticCost() {
            ModelProvider provider = new ModelProvider();
            provider.setCreditCost(100L);

            CreditEstimate result = calculator.calculate(provider, Map.of());
            assertEquals(100, result.getFinalCost());
            assertEquals("STATIC", result.getSource());
        }

        @Test
        @DisplayName("creditCost 为 null 时返回 0")
        void shouldReturnZeroWhenCreditCostNull() {
            ModelProvider provider = new ModelProvider();
            provider.setCreditCost(null);

            CreditEstimate result = calculator.calculate(provider, Map.of());
            assertEquals(0, result.getFinalCost());
        }
    }

    @Nested
    @DisplayName("结构化规则计算")
    class RulesCalculationTests {

        @Test
        @DisplayName("基础积分 + MULTIPLIER 规则")
        void shouldApplyMultiplierRule() {
            ModelProvider provider = new ModelProvider();
            provider.setCreditCost(10L);
            provider.setPricingRules(Map.of(
                    "baseCredits", 10,
                    "rules", List.of(
                            Map.of(
                                    "param", "resolution",
                                    "type", "MULTIPLIER",
                                    "mode", "MULTIPLY",
                                    "mapping", Map.of("1080p", 2.0, "720p", 1.0),
                                    "default", 1.0
                            )
                    )
            ));

            CreditEstimate result = calculator.calculate(provider, Map.of("resolution", "1080p"));
            assertEquals(20, result.getFinalCost());
            assertEquals("PRICING_RULES", result.getSource());
        }

        @Test
        @DisplayName("溢出保护：超大乘数不导致 Long 溢出")
        void shouldHandleOverflow() {
            ModelProvider provider = new ModelProvider();
            provider.setCreditCost(Long.MAX_VALUE / 4);
            provider.setPricingRules(Map.of(
                    "baseCredits", Long.MAX_VALUE / 4,
                    "rules", List.of(
                            Map.of(
                                    "param", "scale",
                                    "type", "MULTIPLIER",
                                    "mode", "MULTIPLY",
                                    "mapping", Map.of("huge", 100.0),
                                    "default", 1.0
                            )
                    )
            ));

            CreditEstimate result = calculator.calculate(provider, Map.of("scale", "huge"));
            // 应该被截断而不是溢出为负数
            assertTrue(result.getFinalCost() > 0);
        }
    }

    @Nested
    @DisplayName("脚本计算 fail-closed")
    class ScriptCalculationTests {

        @Test
        @DisplayName("有效脚本应正常计算")
        void shouldCalculateWithValidScript() {
            ModelProvider provider = new ModelProvider();
            provider.setPluginId("test-provider");
            provider.setCreditCost(10L);
            provider.setPricingScript("return baseCredits * 2");

            CreditEstimate result = calculator.calculate(provider, Map.of());
            assertEquals(20, result.getFinalCost());
            assertEquals("PRICING_SCRIPT", result.getSource());
        }

        @Test
        @DisplayName("脚本执行失败应抛出 CreditCalculationException")
        void shouldThrowOnScriptFailure() {
            ModelProvider provider = new ModelProvider();
            provider.setPluginId("bad-provider");
            provider.setCreditCost(10L);
            provider.setPricingScript("throw new RuntimeException('price error')");

            assertThrows(CreditCalculationException.class,
                    () -> calculator.calculate(provider, Map.of()));
        }

        @Test
        @DisplayName("脚本语法错误应抛出 CreditCalculationException")
        void shouldThrowOnScriptSyntaxError() {
            ModelProvider provider = new ModelProvider();
            provider.setPluginId("syntax-error-provider");
            provider.setCreditCost(10L);
            provider.setPricingScript("this is not valid groovy {{{");

            assertThrows(CreditCalculationException.class,
                    () -> calculator.calculate(provider, Map.of()));
        }
    }

    @Nested
    @DisplayName("输入参数处理")
    class InputHandlingTests {

        @Test
        @DisplayName("null 输入参数不报错")
        void shouldHandleNullInputs() {
            ModelProvider provider = new ModelProvider();
            provider.setCreditCost(50L);

            CreditEstimate result = calculator.calculate(provider, null);
            assertEquals(50, result.getFinalCost());
        }
    }
}
