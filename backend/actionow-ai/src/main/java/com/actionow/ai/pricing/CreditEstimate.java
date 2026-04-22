package com.actionow.ai.pricing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 积分计算结果
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditEstimate {

    /**
     * 规则计算的原始积分（折扣前）
     */
    private long baseCost;

    /**
     * 折扣率 (0.0-1.0, 1.0 = 无折扣)
     */
    @Builder.Default
    private double discountRate = 1.0;

    /**
     * 折扣说明
     */
    private String discountDescription;

    /**
     * 最终积分
     */
    private long finalCost;

    /**
     * 计算来源: PRICING_SCRIPT / PRICING_RULES / STATIC
     */
    private String source;

    /**
     * 计费明细（供前端展示）
     */
    private List<BreakdownItem> breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownItem {

        /**
         * 参数名
         */
        private String param;

        /**
         * 显示标签，如 "分辨率: 1080p"
         */
        private String label;

        /**
         * 效果说明，如 "×2.0" 或 "+10"
         */
        private String effect;

        /**
         * 作用方式: MULTIPLY / ADD
         */
        private String mode;
    }
}
