package com.actionow.billing.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 创建充值订单响应
 */
@Data
@Builder
public class CreateTopupOrderResponse {
    private String orderNo;
    private String status;
    private String workspaceId;
    private Long amountMinor;
    private String currency;
    private String provider;
    private Long pointsAmount;
}
