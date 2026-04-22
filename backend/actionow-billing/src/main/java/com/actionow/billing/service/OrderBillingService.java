package com.actionow.billing.service;

import com.actionow.billing.dto.*;

public interface OrderBillingService {

    CreateTopupOrderResponse createTopupOrder(CreateTopupOrderRequest request, String workspaceId, String userId);

    CreateCheckoutSessionResponse createTopupCheckoutSession(String orderNo,
                                                             CreateCheckoutSessionRequest request,
                                                             String workspaceId);

    OrderDetailResponse getOrder(String orderNo, String workspaceId);

    /**
     * 主动查询支付渠道确认订单状态（前端回调后调用，替代/补充 Webhook）
     *
     * @return 查询后的最新订单详情
     */
    OrderDetailResponse verifyPayment(String orderNo, String workspaceId);

    void handleTopupPaid(String orderNo,
                         String providerPaymentId,
                         String providerSessionId,
                         String provider);

    void handleTopupFailed(String orderNo, String failCode, String failMessage);

    /**
     * 查询充值积分汇率
     */
    TopupRateResponse getTopupRate(String currency);
}
