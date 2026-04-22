package com.actionow.agent.constant;

import lombok.Getter;

/**
 * 计费会话状态枚举
 *
 * 计费会话生命周期：
 * - ACTIVE: 活跃状态，正在记录消费
 * - SETTLING: 结算中，正在与钱包服务交互
 * - SETTLED: 已结算，消费已确认
 * - FAILED: 结算失败，需要补偿处理
 *
 * @author Actionow
 */
@Getter
public enum BillingSessionStatus {

    /**
     * 活跃状态
     * - 计费会话正在进行中
     * - 可以记录 Token 消费和 AI 工具调用
     * - 冻结金额已锁定
     */
    ACTIVE("ACTIVE", "活跃"),

    /**
     * 结算中
     * - 会话已结束，正在与钱包服务确认消费
     * - 乐观锁保护，防止并发结算
     * - 短暂过渡状态
     */
    SETTLING("SETTLING", "结算中"),

    /**
     * 已结算
     * - 消费已确认，冻结金额已释放
     * - 实际消费已扣除
     * - 最终状态
     */
    SETTLED("SETTLED", "已结算"),

    /**
     * 结算失败
     * - 钱包服务调用失败
     * - 需要补偿调度器重试
     * - 记录失败原因
     */
    FAILED("FAILED", "结算失败");

    /**
     * 状态代码
     */
    private final String code;

    /**
     * 状态名称（中文）
     */
    private final String name;

    BillingSessionStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据 code 获取枚举
     */
    public static BillingSessionStatus fromCode(String code) {
        if (code == null) {
            return ACTIVE;
        }
        for (BillingSessionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return ACTIVE;
    }

    /**
     * 判断是否为活跃状态
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 判断是否为最终状态
     */
    public boolean isFinal() {
        return this == SETTLED || this == FAILED;
    }

    /**
     * 判断是否可以记录消费
     */
    public boolean canRecordUsage() {
        return this == ACTIVE;
    }

    /**
     * 判断是否需要补偿处理
     */
    public boolean needsCompensation() {
        return this == FAILED;
    }

    /**
     * 判断是否可以转换到目标状态
     */
    public boolean canTransitionTo(BillingSessionStatus target) {
        return switch (this) {
            case ACTIVE -> target == SETTLING;
            case SETTLING -> target == SETTLED || target == FAILED;
            case SETTLED -> false; // 最终状态，不可转换
            case FAILED -> target == SETTLING; // 可以重试
        };
    }
}
