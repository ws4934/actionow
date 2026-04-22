package com.actionow.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 变更计划响应
 */
@Data
@Builder
public class ChangePlanResponse {
    private String workspaceId;
    private String oldPlan;
    private String targetPlan;
    private String subscriptionId;
    private String status;
    private LocalDateTime effectiveAt;
    private String checkoutUrl;
}
