package com.actionow.billing.dto;

import lombok.Data;

/**
 * 取消订阅请求
 */
@Data
public class CancelSubscriptionRequest {
    private Boolean cancelAtPeriodEnd = Boolean.TRUE;
    private String reason;
}
