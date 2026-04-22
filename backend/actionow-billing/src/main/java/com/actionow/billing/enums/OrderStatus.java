package com.actionow.billing.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 支付订单状态
 *
 * 状态流转:
 * INIT → PENDING, PAID, FAILED, CANCELED, EXPIRED
 * PENDING → PAID, FAILED, CANCELED, EXPIRED
 * PAID → REFUNDED, PARTIALLY_REFUNDED
 * FAILED, CANCELED, EXPIRED, REFUNDED, PARTIALLY_REFUNDED → (terminal)
 */
public enum OrderStatus {
    INIT,
    PENDING,
    PAID,
    FAILED,
    CANCELED,
    EXPIRED,
    REFUNDED,
    PARTIALLY_REFUNDED;

    /**
     * 判断是否可以转换到目标状态
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) {
            return true;
        }
        return getAllowedTransitions().contains(target);
    }

    /**
     * 获取当前状态允许转换到的状态集合
     */
    public Set<OrderStatus> getAllowedTransitions() {
        return switch (this) {
            case INIT -> EnumSet.of(PENDING, PAID, FAILED, CANCELED, EXPIRED);
            case PENDING -> EnumSet.of(PAID, FAILED, CANCELED, EXPIRED);
            case PAID -> EnumSet.of(REFUNDED, PARTIALLY_REFUNDED);
            case FAILED, CANCELED, EXPIRED, REFUNDED, PARTIALLY_REFUNDED ->
                    EnumSet.noneOf(OrderStatus.class);
        };
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return switch (this) {
            case FAILED, CANCELED, EXPIRED, REFUNDED, PARTIALLY_REFUNDED -> true;
            default -> false;
        };
    }
}
