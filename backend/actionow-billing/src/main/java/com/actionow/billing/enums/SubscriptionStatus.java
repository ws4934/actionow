package com.actionow.billing.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 订阅状态
 *
 * 状态流转:
 * TRIALING → ACTIVE, PAST_DUE, CANCELED, EXPIRED
 * ACTIVE → PAST_DUE, CANCELED, EXPIRED
 * PAST_DUE → ACTIVE, CANCELED, EXPIRED
 * CANCELED → ACTIVE (reactivation)
 * EXPIRED → (terminal)
 */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED;

    /**
     * 判断是否可以转换到目标状态
     */
    public boolean canTransitionTo(SubscriptionStatus target) {
        if (this == target) {
            return true;
        }
        return getAllowedTransitions().contains(target);
    }

    /**
     * 获取当前状态允许转换到的状态集合
     */
    public Set<SubscriptionStatus> getAllowedTransitions() {
        return switch (this) {
            case TRIALING -> EnumSet.of(ACTIVE, PAST_DUE, CANCELED, EXPIRED);
            case ACTIVE -> EnumSet.of(PAST_DUE, CANCELED, EXPIRED);
            case PAST_DUE -> EnumSet.of(ACTIVE, CANCELED, EXPIRED);
            case CANCELED -> EnumSet.of(ACTIVE); // reactivation
            case EXPIRED -> EnumSet.noneOf(SubscriptionStatus.class);
        };
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return this == EXPIRED;
    }

    public static SubscriptionStatus from(String value) {
        if (value == null) {
            return null;
        }
        for (SubscriptionStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
