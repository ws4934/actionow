package com.actionow.billing.provider;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Checkout Session 创建结果
 */
@Data
@Builder
public class CheckoutSessionResult {
    private String sessionId;
    private String checkoutUrl;
    private String paymentId;
    private LocalDateTime expiresAt;
}
