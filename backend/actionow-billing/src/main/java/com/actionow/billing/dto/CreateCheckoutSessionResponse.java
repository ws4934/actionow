package com.actionow.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建 Checkout Session 响应
 */
@Data
@Builder
public class CreateCheckoutSessionResponse {
    private String orderNo;
    private String providerSessionId;
    private String checkoutUrl;
    private LocalDateTime expiresAt;
}
