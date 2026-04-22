package com.actionow.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 套餐价格响应
 */
@Data
@Builder
public class PlanPriceResponse {

    private String id;
    private String provider;
    private String planCode;
    private String workspacePlanType;
    private String billingCycle;
    private String currency;
    private Long amountMinor;
    private Boolean metered;
    private String usageType;
    private String stripeProductId;
    private String stripePriceId;
    private String status;
    private Map<String, Object> meta;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

