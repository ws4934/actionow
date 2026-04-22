package com.actionow.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前订阅响应
 */
@Data
@Builder
public class SubscriptionResponse {
    private String subscriptionId;
    private String workspaceId;
    private String provider;
    private String planCode;
    private String billingCycle;
    private String status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime gracePeriodEnd;
}
