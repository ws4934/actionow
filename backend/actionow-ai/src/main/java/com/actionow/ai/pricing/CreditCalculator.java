package com.actionow.ai.pricing;

import com.actionow.ai.entity.ModelProvider;
import com.actionow.ai.plugin.groovy.GroovyScriptCache;
import groovy.lang.Binding;
import groovy.lang.Script;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 动态积分计算器
 * <p>
 * 优先级: pricing_script > pricing_rules > credit_cost (静态兜底)
 * <p>
 * pricing_rules JSON 结构:
 * <pre>
 * {
 *   "baseCredits": 10,
 *   "minCredits": 1,
 *   "maxCredits": 500,
 *   "roundMode": "CEILING",
 *   "rules": [
 *     { "param": "resolution", "type": "MULTIPLIER", "mode": "MULTIPLY", "mapping": {...}, "default": 1.0 },
 *     { "param": "duration",   "type": "LINEAR",     "mode": "ADD",      "perUnit": 2.0 },
 *     { "param": "quality",    "type": "TIERED",     "mode": "MULTIPLY", "tiers": [...], "default": 1.0 }
 *   ],
 *   "discount": { "rate": 0.8, "validFrom": "2026-01-01", "validTo": "2026-12-31", "description": "新模型八折" }
 * }
 * </pre>
 * <p>
 * 组合公式: finalCredits = (baseCredits × Π(MULTIPLY rules)) + Σ(ADD rules)
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditCalculator {

    private final GroovyScriptCache scriptCache;

    /**
     * 计算积分消耗（含折扣）
     *
     * @param provider   模型提供商
     * @param userInputs 用户输入参数
     * @return 计算结果
     */
    public CreditEstimate calculate(ModelProvider provider, Map<String, Object> userInputs) {
        Map<String, Object> inputs = userInputs != null ? new HashMap<>(userInputs) : new HashMap<>();

        // 补充 inputSchema 中的默认值（用户未传的参数取 defaultValue）
        applyInputSchemaDefaults(inputs, provider.getInputSchema());

        // 1. 尝试 Groovy 脚本
        String pricingScript = provider.getPricingScript();
        if (pricingScript != null && !pricingScript.isBlank()) {
            return calculateByScript(provider, inputs, pricingScript);
        }

        // 2. 尝试结构化规则
        Map<String, Object> pricingRules = provider.getPricingRules();
        if (pricingRules != null && !pricingRules.isEmpty()) {
            return calculateByRules(provider, inputs, pricingRules);
        }

        // 3. 兜底: 静态 credit_cost
        long staticCost = provider.getCreditCost() != null ? provider.getCreditCost() : 0L;
        return CreditEstimate.builder()
                .baseCost(staticCost)
                .finalCost(staticCost)
                .source("STATIC")
                .breakdown(List.of())
                .build();
    }

    /**
     * 通过结构化规则计算（含折扣）
     */
    private CreditEstimate calculateByRules(ModelProvider provider,
                                            Map<String, Object> inputs,
                                            Map<String, Object> pricingRules) {
        long baseCredits = toLong(pricingRules.get("baseCredits"),
                provider.getCreditCost() != null ? provider.getCreditCost() : 0L);
        long minCredits = toLong(pricingRules.get("minCredits"), 0L);
        long maxCredits = toLong(pricingRules.get("maxCredits"), Long.MAX_VALUE);
        String roundMode = toStr(pricingRules.get("roundMode"), "CEILING");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) pricingRules.get("rules");
        if (rules == null) {
            rules = List.of();
        }

        double multiplyAccum = 1.0;
        double addAccum = 0.0;
        List<CreditEstimate.BreakdownItem> breakdown = new ArrayList<>();

        for (Map<String, Object> rule : rules) {
            String param = toStr(rule.get("param"), "");
            String type = toStr(rule.get("type"), "MULTIPLIER");
            String mode = toStr(rule.get("mode"), "MULTIPLY");
            Object paramValue = resolveParamValue(inputs, param);

            double ruleValue = evaluateRule(type, rule, paramValue);

            String effectStr;
            if ("ADD".equalsIgnoreCase(mode)) {
                addAccum += ruleValue;
                effectStr = (ruleValue >= 0 ? "+" : "") + formatNumber(ruleValue);
            } else {
                multiplyAccum *= ruleValue;
                effectStr = "×" + formatNumber(ruleValue);
            }

            breakdown.add(CreditEstimate.BreakdownItem.builder()
                    .param(param)
                    .label(param + ": " + (paramValue != null ? paramValue : "default"))
                    .effect(effectStr)
                    .mode(mode.toUpperCase())
                    .build());
        }

        // 组合公式: (baseCredits × multiply) + add — 溢出保护
        double rawResult = (baseCredits * multiplyAccum) + addAccum;
        if (Double.isInfinite(rawResult) || Double.isNaN(rawResult) || rawResult > Long.MAX_VALUE / 2) {
            log.warn("积分计算结果溢出，截断至安全范围: rawResult={}", rawResult);
            rawResult = Long.MAX_VALUE / 2;
        }
        long baseCost = applyRounding(rawResult, roundMode);
        baseCost = Math.max(minCredits, Math.min(baseCost, maxCredits));

        // 应用折扣
        double discountRate = 1.0;
        String discountDesc = null;

        @SuppressWarnings("unchecked")
        Map<String, Object> discount = (Map<String, Object>) pricingRules.get("discount");
        if (discount != null) {
            discountRate = resolveDiscount(discount);
            if (discountRate < 1.0) {
                discountDesc = toStr(discount.get("description"), null);
            }
        }

        long finalCost = Math.max(minCredits, applyRounding(baseCost * discountRate, roundMode));

        return CreditEstimate.builder()
                .baseCost(baseCost)
                .discountRate(discountRate)
                .discountDescription(discountDesc)
                .finalCost(finalCost)
                .source("PRICING_RULES")
                .breakdown(breakdown)
                .build();
    }

    /**
     * 通过 Groovy 脚本计算
     */
    private CreditEstimate calculateByScript(ModelProvider provider,
                                             Map<String, Object> inputs,
                                             String script) {
        try {
            long baseCredits = provider.getCreditCost() != null ? provider.getCreditCost() : 0L;

            Map<String, Object> bindings = new HashMap<>();
            bindings.put("inputs", inputs);
            bindings.put("baseCredits", baseCredits);
            bindings.put("providerType", provider.getProviderType());
            bindings.put("providerId", provider.getPluginId());

            Class<? extends Script> scriptClass = scriptCache.getOrCompile(script);
            Script groovyScript = scriptClass.getDeclaredConstructor().newInstance();
            groovyScript.setBinding(new Binding(bindings));
            Object result = groovyScript.run();

            long cost = toLong(result, baseCredits);
            cost = Math.max(0, cost);

            return CreditEstimate.builder()
                    .baseCost(cost)
                    .finalCost(cost)
                    .source("PRICING_SCRIPT")
                    .breakdown(List.of())
                    .build();

        } catch (Exception e) {
            log.error("Pricing script execution failed for provider={}, refusing to calculate (fail-closed)",
                    provider.getPluginId(), e);
            throw new CreditCalculationException(
                    "定价脚本执行失败 (provider=" + provider.getPluginId() + "): " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════
    // 规则求值
    // ═══════════════════════════════════════════

    private double evaluateRule(String type, Map<String, Object> rule, Object paramValue) {
        return switch (type.toUpperCase()) {
            case "MULTIPLIER" -> evaluateMultiplier(rule, paramValue);
            case "LINEAR" -> evaluateLinear(rule, paramValue);
            case "TIERED" -> evaluateTiered(rule, paramValue);
            case "PERCENTAGE" -> evaluatePercentage(rule, paramValue);
            default -> toDouble(rule.get("default"), 1.0);
        };
    }

    /**
     * MULTIPLIER: 枚举映射 → 系数
     */
    @SuppressWarnings("unchecked")
    private double evaluateMultiplier(Map<String, Object> rule, Object paramValue) {
        Map<String, Object> mapping = (Map<String, Object>) rule.get("mapping");
        if (mapping == null || paramValue == null) {
            return toDouble(rule.get("default"), 1.0);
        }
        String key = String.valueOf(paramValue);
        Object factor = mapping.get(key);
        return factor != null ? toDouble(factor, 1.0) : toDouble(rule.get("default"), 1.0);
    }

    /**
     * LINEAR: perUnit × paramValue
     */
    private double evaluateLinear(Map<String, Object> rule, Object paramValue) {
        double perUnit = toDouble(rule.get("perUnit"), 0.0);
        double value = toDouble(paramValue, 0.0);

        double min = toDouble(rule.get("min"), 0.0);
        double max = toDouble(rule.get("max"), Double.MAX_VALUE);
        value = Math.max(min, Math.min(value, max));

        return perUnit * value;
    }

    /**
     * TIERED: 查找 paramValue 所在区间的 value
     */
    @SuppressWarnings("unchecked")
    private double evaluateTiered(Map<String, Object> rule, Object paramValue) {
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) rule.get("tiers");
        if (tiers == null || paramValue == null) {
            return toDouble(rule.get("default"), 1.0);
        }
        double numValue = toDouble(paramValue, 0.0);
        for (Map<String, Object> tier : tiers) {
            double from = toDouble(tier.get("from"), 0.0);
            double to = toDouble(tier.get("to"), Double.MAX_VALUE);
            if (numValue >= from && numValue <= to) {
                return toDouble(tier.get("value"), 1.0);
            }
        }
        return toDouble(rule.get("default"), 1.0);
    }

    /**
     * PERCENTAGE: 枚举映射 → 百分比 / 100
     */
    @SuppressWarnings("unchecked")
    private double evaluatePercentage(Map<String, Object> rule, Object paramValue) {
        Map<String, Object> mapping = (Map<String, Object>) rule.get("mapping");
        if (mapping == null || paramValue == null) {
            return toDouble(rule.get("default"), 1.0);
        }
        String key = String.valueOf(paramValue);
        Object pct = mapping.get(key);
        return pct != null ? toDouble(pct, 100.0) / 100.0 : toDouble(rule.get("default"), 1.0);
    }

    // ═══════════════════════════════════════════
    // 折扣
    // ═══════════════════════════════════════════

    private double resolveDiscount(Map<String, Object> discount) {
        double rate = toDouble(discount.get("rate"), 1.0);
        if (rate >= 1.0) {
            return 1.0;
        }

        // 检查有效期
        String validFrom = toStr(discount.get("validFrom"), null);
        String validTo = toStr(discount.get("validTo"), null);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        if (validFrom != null) {
            LocalDate from = LocalDate.parse(validFrom);
            if (today.isBefore(from)) {
                return 1.0;
            }
        }
        if (validTo != null) {
            LocalDate to = LocalDate.parse(validTo);
            if (today.isAfter(to)) {
                return 1.0;
            }
        }

        return Math.max(0.0, Math.min(rate, 1.0));
    }

    // ═══════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════

    /**
     * 将 inputSchema 中的 defaultValue/default 填充到 inputs（用户未传的参数）
     * 保证计价时使用与实际生成一致的参数值
     */
    @SuppressWarnings("unchecked")
    private void applyInputSchemaDefaults(Map<String, Object> inputs,
                                           List<Map<String, Object>> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return;
        }
        for (Map<String, Object> field : inputSchema) {
            String name = toStr(field.get("name"), null);
            if (name == null || inputs.containsKey(name)) {
                continue;
            }
            // inputSchema 中 defaultValue 优先，其次 default
            Object defaultVal = field.get("defaultValue");
            if (defaultVal == null) {
                defaultVal = field.get("default");
            }
            if (defaultVal != null) {
                inputs.put(name, defaultVal);
            }
        }
    }

    private Object resolveParamValue(Map<String, Object> inputs, String param) {
        if (inputs == null || param == null) {
            return null;
        }
        // 支持嵌套路径: "imageUrls.size" → inputs.get("imageUrls") 的 size
        if (param.endsWith(".size") || param.endsWith(".length")) {
            String listParam = param.substring(0, param.lastIndexOf('.'));
            Object listValue = inputs.get(listParam);
            if (listValue instanceof Collection<?> c) {
                return c.size();
            }
            return null;
        }
        return inputs.get(param);
    }

    private long applyRounding(double value, String roundMode) {
        return switch (roundMode != null ? roundMode.toUpperCase() : "CEILING") {
            case "FLOOR" -> (long) Math.floor(value);
            case "HALF_UP" -> Math.round(value);
            default -> (long) Math.ceil(value);
        };
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("toLong 转换失败，使用默认值 {}: value={}", defaultValue, value);
            return defaultValue;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String toStr(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }
}
