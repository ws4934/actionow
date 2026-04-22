package com.actionow.billing.provider;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标准化订阅信息
 */
@Data
@Builder
public class ProviderSubscriptionInfo {
    private String subscriptionId;
    private String customerId;
    private String status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime canceledAt;
}
