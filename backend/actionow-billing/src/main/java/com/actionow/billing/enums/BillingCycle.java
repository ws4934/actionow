package com.actionow.billing.enums;

/**
 * 计费周期
 */
public enum BillingCycle {
    MONTHLY,
    YEARLY;

    public static BillingCycle from(String value) {
        for (BillingCycle cycle : values()) {
            if (cycle.name().equalsIgnoreCase(value)) {
                return cycle;
            }
        }
        throw new IllegalArgumentException("Unsupported billing cycle: " + value);
    }
}
