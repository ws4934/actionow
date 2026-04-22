package com.actionow.billing.service.impl;

import com.actionow.billing.dto.CallbackAckResponse;
import com.actionow.billing.entity.ProviderEvent;
import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.billing.mapper.ProviderEventMapper;
import com.actionow.billing.provider.PaymentProviderAdapter;
import com.actionow.billing.provider.PaymentProviderRouter;
import com.actionow.billing.provider.ProviderWebhookEvent;
import com.actionow.billing.service.OrderBillingService;
import com.actionow.billing.service.SubscriptionBillingService;
import com.actionow.billing.service.WebhookBillingService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 支付回调处理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookBillingServiceImpl implements WebhookBillingService {

    private static final String EVENT_STATUS_PENDING = "PENDING";
    private static final String EVENT_STATUS_PROCESSED = "PROCESSED";
    private static final String CHECKOUT_MODE_PAYMENT = "payment";
    private static final String CHECKOUT_MODE_SUBSCRIPTION = "subscription";

    private final PaymentProviderRouter providerRouter;
    private final ProviderEventMapper providerEventMapper;
    private final OrderBillingService orderBillingService;
    private final SubscriptionBillingService subscriptionBillingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CallbackAckResponse handleCallback(String provider, String payload, Map<String, String> headers) {
        PaymentProvider paymentProvider = PaymentProvider.from(provider);
        PaymentProviderAdapter adapter = providerRouter.getAdapter(paymentProvider);

        ProviderWebhookEvent webhookEvent;
        try {
            webhookEvent = adapter.verifyAndParseWebhook(payload, headers);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID,
                    "回调验签或解析失败: " + e.getMessage());
        }

        ProviderEvent existing = providerEventMapper.selectByProviderAndEventId(
                webhookEvent.getProvider(), webhookEvent.getEventId());
        if (existing != null && EVENT_STATUS_PROCESSED.equals(existing.getProcessStatus())) {
            return CallbackAckResponse.builder()
                    .accepted(true)
                    .provider(provider)
                    .eventId(webhookEvent.getEventId())
                    .build();
        }

        if (existing == null) {
            ProviderEvent record = new ProviderEvent();
            record.setId(UuidGenerator.generateUuidV7());
            record.setProvider(webhookEvent.getProvider());
            record.setEventId(webhookEvent.getEventId());
            record.setEventType(webhookEvent.getEventType());
            record.setResourceId(webhookEvent.getResourceId());
            record.setEventCreatedAt(extractEventTime(webhookEvent));
            record.setSignatureVerified(Boolean.TRUE);
            record.setProcessStatus(EVENT_STATUS_PENDING);
            record.setPayloadRaw(payload);
            providerEventMapper.insertIgnore(record);
        }

        try {
            dispatchWebhookEvent(webhookEvent);
            providerEventMapper.markProcessed(webhookEvent.getProvider(), webhookEvent.getEventId(), "ok");
        } catch (BusinessException e) {
            providerEventMapper.markFailed(webhookEvent.getProvider(), webhookEvent.getEventId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            providerEventMapper.markFailed(webhookEvent.getProvider(), webhookEvent.getEventId(), e.getMessage());
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID, "处理回调失败: " + e.getMessage());
        }

        return CallbackAckResponse.builder()
                .accepted(true)
                .provider(provider)
                .eventId(webhookEvent.getEventId())
                .build();
    }

    private void dispatchWebhookEvent(ProviderWebhookEvent webhookEvent) {
        PaymentProvider provider = PaymentProvider.from(webhookEvent.getProvider());
        switch (provider) {
            case STRIPE -> dispatchStripeEvent(webhookEvent);
            case WECHATPAY -> dispatchWechatPayEvent(webhookEvent);
            default -> log.warn("未支持的支付渠道回调: {}", webhookEvent.getProvider());
        }
    }

    // ==================== Stripe 事件分发 ====================

    private void dispatchStripeEvent(ProviderWebhookEvent webhookEvent) {
        if (!(webhookEvent.getRawEvent() instanceof Event event)) {
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID, "不支持的 Stripe 回调事件类型");
        }

        String eventType = event.getType();
        StripeObject object = extractStripeObject(event);

        switch (eventType) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                if (!(object instanceof Session session)) {
                    throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID, "Stripe checkout session 解析失败");
                }
                if (CHECKOUT_MODE_PAYMENT.equalsIgnoreCase(session.getMode())) {
                    String orderNo = extractOrderNo(session);
                    if (orderNo == null || orderNo.isBlank()) {
                        throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID, "支付回调缺少 orderNo");
                    }
                    orderBillingService.handleTopupPaid(orderNo, session.getPaymentIntent(), session.getId(), PaymentProvider.STRIPE.name());
                } else if (CHECKOUT_MODE_SUBSCRIPTION.equalsIgnoreCase(session.getMode())) {
                    subscriptionBillingService.handleStripeSubscriptionCheckoutCompleted(session);
                }
            }
            case "checkout.session.async_payment_failed" -> {
                if (object instanceof Session session) {
                    String orderNo = extractOrderNo(session);
                    if (orderNo != null && !orderNo.isBlank()) {
                        orderBillingService.handleTopupFailed(orderNo, "ASYNC_PAYMENT_FAILED", "Stripe async payment failed");
                    }
                }
            }
            case "customer.subscription.updated" -> {
                if (object instanceof Subscription subscription) {
                    subscriptionBillingService.handleStripeSubscriptionUpdated(subscription);
                }
            }
            case "customer.subscription.deleted" -> {
                if (object instanceof Subscription subscription) {
                    subscriptionBillingService.handleStripeSubscriptionDeleted(subscription);
                }
            }
            case "invoice.paid", "invoice.payment_failed" ->
                    log.debug("收到 Stripe 发票事件: type={}", eventType);
            default -> log.debug("忽略未处理 Stripe 事件: type={}", eventType);
        }
    }

    // ==================== 微信支付事件分发 ====================

    private void dispatchWechatPayEvent(ProviderWebhookEvent webhookEvent) {
        if (!(webhookEvent.getRawEvent() instanceof Transaction transaction)) {
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID, "不支持的微信支付回调事件类型");
        }

        String outTradeNo = transaction.getOutTradeNo();
        Transaction.TradeStateEnum tradeState = transaction.getTradeState();

        if (tradeState == Transaction.TradeStateEnum.SUCCESS) {
            orderBillingService.handleTopupPaid(
                    outTradeNo,
                    transaction.getTransactionId(),
                    null,
                    PaymentProvider.WECHATPAY.name());
        } else if (tradeState == Transaction.TradeStateEnum.CLOSED
                || tradeState == Transaction.TradeStateEnum.PAYERROR) {
            orderBillingService.handleTopupFailed(
                    outTradeNo,
                    tradeState.name(),
                    "微信支付失败: " + tradeState.name());
        } else {
            log.debug("忽略微信支付非终态事件: outTradeNo={}, tradeState={}", outTradeNo, tradeState);
        }
    }

    // ==================== 工具方法 ====================

    private StripeObject extractStripeObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject().orElse(null);
    }

    private String extractOrderNo(Session session) {
        if (session.getMetadata() != null) {
            String orderNo = session.getMetadata().get("orderNo");
            if (orderNo != null && !orderNo.isBlank()) {
                return orderNo;
            }
        }
        return session.getClientReferenceId();
    }

    private LocalDateTime extractEventTime(ProviderWebhookEvent webhookEvent) {
        Object rawEvent = webhookEvent.getRawEvent();
        if (rawEvent instanceof Event event && event.getCreated() != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(event.getCreated()), ZoneId.of("UTC"));
        }
        if (rawEvent instanceof Transaction transaction && transaction.getSuccessTime() != null) {
            try {
                // 微信 success_time 格式为 RFC 3339: 2023-01-01T00:00:00+08:00
                OffsetDateTime odt = OffsetDateTime.parse(transaction.getSuccessTime());
                return odt.atZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
            } catch (Exception e) {
                log.debug("解析微信支付时间失败: {}", transaction.getSuccessTime());
            }
        }
        return null;
    }
}
