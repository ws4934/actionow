package com.actionow.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单详情响应
 */
@Data
@Builder
public class OrderDetailResponse {
    private String orderNo;
    private String workspaceId;
    private String userId;
    private String provider;
    private String orderType;
    private String status;
    private Long amountMinor;
    private String currency;
    private Long pointsAmount;
    private String providerPaymentId;
    private String providerSessionId;
    private String failCode;
    private String failMessage;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, Object> meta;
}
