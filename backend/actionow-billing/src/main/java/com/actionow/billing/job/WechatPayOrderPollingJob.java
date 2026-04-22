package com.actionow.billing.job;

import com.actionow.billing.entity.PaymentOrder;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.billing.mapper.PaymentOrderMapper;
import com.actionow.billing.provider.stripe.StripePaymentProviderAdapter;
import com.actionow.billing.provider.wechatpay.WechatPayProviderAdapter;
import com.actionow.billing.service.OrderBillingService;
import com.stripe.model.checkout.Session;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付订单轮询任务（通用）
 * 定期查询 PENDING 状态的订单，补偿回调未到达的场景
 * 支持 Stripe 和 微信支付
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatPayOrderPollingJob {

    /** 订单创建后多久开始轮询（给正常回调留窗口） */
    private static final long POLL_DELAY_MINUTES = 2;
    /** 订单超时时间 */
    private static final long ORDER_EXPIRE_HOURS = 2;

    private final PaymentOrderMapper paymentOrderMapper;
    private final WechatPayProviderAdapter wechatPayAdapter;
    private final StripePaymentProviderAdapter stripeAdapter;
    private final OrderBillingService orderBillingService;

    @Scheduled(fixedDelay = 30_000)
    public void pollPendingOrders() {
        LocalDateTime createdBefore = LocalDateTime.now().minusMinutes(POLL_DELAY_MINUTES);
        List<PaymentOrder> pendingOrders = paymentOrderMapper.selectAllPending(createdBefore);

        if (pendingOrders.isEmpty()) {
            return;
        }

        log.debug("支付轮询: 待查询订单数={}", pendingOrders.size());

        for (PaymentOrder order : pendingOrders) {
            try {
                PaymentProvider provider = PaymentProvider.from(order.getProvider());
                switch (provider) {
                    case STRIPE -> processStripeOrder(order);
                    case WECHATPAY -> processWechatOrder(order);
                    default -> log.debug("轮询跳过不支持的渠道: provider={}", provider);
                }
            } catch (Exception e) {
                log.warn("支付轮询处理失败: orderNo={}, provider={}, error={}",
                        order.getOrderNo(), order.getProvider(), e.getMessage());
            }
        }
    }

    private void processStripeOrder(PaymentOrder order) throws Exception {
        if (isExpired(order)) {
            orderBillingService.handleTopupFailed(order.getOrderNo(), "EXPIRED", "Stripe 支付订单超时");
            return;
        }

        String sessionId = order.getProviderSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Session session = stripeAdapter.retrieveCheckoutSession(sessionId);
        if ("paid".equals(session.getPaymentStatus())) {
            log.info("轮询发现 Stripe 已支付: orderNo={}, paymentIntent={}",
                    order.getOrderNo(), session.getPaymentIntent());
            orderBillingService.handleTopupPaid(
                    order.getOrderNo(),
                    session.getPaymentIntent(),
                    session.getId(),
                    PaymentProvider.STRIPE.name());
        } else if ("expired".equals(session.getStatus())) {
            orderBillingService.handleTopupFailed(order.getOrderNo(), "EXPIRED", "Stripe Session 已过期");
        }
    }

    private void processWechatOrder(PaymentOrder order) {
        if (isExpired(order)) {
            log.info("微信支付订单超时关单: orderNo={}", order.getOrderNo());
            wechatPayAdapter.closeOrder(order.getOrderNo());
            orderBillingService.handleTopupFailed(order.getOrderNo(), "EXPIRED", "微信支付订单超时");
            return;
        }

        Transaction transaction = wechatPayAdapter.queryOrder(order.getOrderNo());
        if (transaction == null) {
            return;
        }

        Transaction.TradeStateEnum tradeState = transaction.getTradeState();

        if (tradeState == Transaction.TradeStateEnum.SUCCESS) {
            log.info("轮询发现微信已支付: orderNo={}, transactionId={}",
                    order.getOrderNo(), transaction.getTransactionId());
            orderBillingService.handleTopupPaid(
                    order.getOrderNo(),
                    transaction.getTransactionId(),
                    null,
                    PaymentProvider.WECHATPAY.name());
        } else if (tradeState == Transaction.TradeStateEnum.CLOSED
                || tradeState == Transaction.TradeStateEnum.PAYERROR
                || tradeState == Transaction.TradeStateEnum.REVOKED) {
            orderBillingService.handleTopupFailed(
                    order.getOrderNo(),
                    tradeState.name(),
                    "微信支付状态: " + tradeState.name());
        }
    }

    private boolean isExpired(PaymentOrder order) {
        return order.getCreatedAt() != null
                && order.getCreatedAt().plusHours(ORDER_EXPIRE_HOURS).isBefore(LocalDateTime.now());
    }
}
