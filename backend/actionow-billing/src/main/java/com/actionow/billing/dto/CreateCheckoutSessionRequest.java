package com.actionow.billing.dto;

import lombok.Data;

/**
 * 创建 Checkout Session 请求
 */
@Data
public class CreateCheckoutSessionRequest {
    private String successUrl;
    private String cancelUrl;
    private String clientReferenceId;
}
