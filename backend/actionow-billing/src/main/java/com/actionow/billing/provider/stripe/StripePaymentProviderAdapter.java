package com.actionow.billing.provider.stripe;

import com.actionow.billing.config.BillingProperties;
import com.actionow.billing.entity.PaymentOrder;
import com.actionow.billing.enums.BillingCycle;
import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.billing.mapper.BillingPlanPriceMapper;
import com.actionow.billing.provider.CheckoutSessionResult;
import com.actionow.billing.provider.PaymentProviderAdapter;
import com.actionow.billing.provider.ProviderSubscriptionInfo;
import com.actionow.billing.provider.ProviderWebhookEvent;
import com.actionow.common.core.exception.BusinessException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Stripe 支付渠道适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentProviderAdapter implements PaymentProviderAdapter {

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final BillingProperties billingProperties;
    private final BillingPlanPriceMapper billingPlanPriceMapper;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public CheckoutSessionResult createTopupCheckoutSession(PaymentOrder order,
                                                            String successUrl,
                                                            String cancelUrl,
                                                            String clientReferenceId) throws StripeException {
        initStripeApiKey();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderNo", order.getOrderNo());
        metadata.put("workspaceId", order.getWorkspaceId());
        metadata.put("orderType", order.getOrderType());

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(resolveSuccessUrl(successUrl))
                .setCancelUrl(resolveCancelUrl(cancelUrl))
                .setClientReferenceId(clientReferenceId != null ? clientReferenceId : order.getOrderNo())
                .putAllMetadata(metadata)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(order.getCurrency().toLowerCase())
                                .setUnitAmount(order.getAmountMinor())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Actionow Topup " + order.getOrderNo())
                                        .build())
                                .build())
                        .build())
                .build();

        Session session = Session.create(params);

        return CheckoutSessionResult.builder()
                .sessionId(session.getId())
                .checkoutUrl(session.getUrl())
                .paymentId(session.getPaymentIntent())
                .expiresAt(toLocalDateTime(session.getExpiresAt()))
                .build();
    }

    @Override
    public CheckoutSessionResult createSubscriptionCheckoutSession(String workspaceId,
                                                                   String planCode,
                                                                   String billingCycle,
                                                                   String successUrl,
                                                                   String cancelUrl,
                                                                   String clientReferenceId,
                                                                   Map<String, String> metadata) throws StripeException {
        initStripeApiKey();

        String priceId = resolvePlanPriceId(planCode, billingCycle);
        if (priceId == null || priceId.isBlank()) {
            throw new BusinessException(BillingErrorCode.PLAN_PRICE_NOT_CONFIGURED);
        }

        Map<String, String> mergedMetadata = new HashMap<>();
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        mergedMetadata.put("workspaceId", workspaceId);
        mergedMetadata.put("targetPlan", planCode);
        mergedMetadata.put("billingCycle", billingCycle);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(resolveSuccessUrl(successUrl))
                .setCancelUrl(resolveCancelUrl(cancelUrl))
                .setClientReferenceId(clientReferenceId != null ? clientReferenceId : workspaceId)
                .putAllMetadata(mergedMetadata)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .build();

        Session session = Session.create(params);

        return CheckoutSessionResult.builder()
                .sessionId(session.getId())
                .checkoutUrl(session.getUrl())
                .paymentId(session.getSubscription())
                .expiresAt(toLocalDateTime(session.getExpiresAt()))
                .build();
    }

    @Override
    public ProviderWebhookEvent verifyAndParseWebhook(String payload, Map<String, String> headers) {
        initStripeApiKey();

        String webhookSecret = billingProperties.getStripe().getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new BusinessException(BillingErrorCode.STRIPE_CONFIG_MISSING, "Stripe webhook secret 未配置");
        }

        String signature = headers.get(STRIPE_SIGNATURE_HEADER);
        if ((signature == null || signature.isBlank()) && headers.containsKey("stripe-signature")) {
            signature = headers.get("stripe-signature");
        }

        if (signature == null || signature.isBlank()) {
            throw new BusinessException(BillingErrorCode.STRIPE_WEBHOOK_VERIFY_FAILED, "缺少 Stripe-Signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (Exception e) {
            throw new BusinessException(BillingErrorCode.STRIPE_WEBHOOK_VERIFY_FAILED,
                    "Stripe webhook 验签失败: " + e.getMessage());
        }

        return ProviderWebhookEvent.builder()
                .provider(PaymentProvider.STRIPE.name())
                .eventId(event.getId())
                .eventType(event.getType())
                .resourceId(resolveResourceId(event))
                .rawEvent(event)
                .build();
    }

    @Override
    public ProviderSubscriptionInfo retrieveSubscription(String providerSubscriptionId) throws StripeException {
        initStripeApiKey();
        Subscription subscription = Subscription.retrieve(providerSubscriptionId);
        return toSubscriptionInfo(subscription);
    }

    @Override
    public ProviderSubscriptionInfo cancelSubscription(String providerSubscriptionId, boolean cancelAtPeriodEnd) throws StripeException {
        initStripeApiKey();

        Subscription subscription = Subscription.retrieve(providerSubscriptionId);
        Subscription updated = subscription.update(SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(cancelAtPeriodEnd)
                .build());

        return toSubscriptionInfo(updated);
    }

    private ProviderSubscriptionInfo toSubscriptionInfo(Subscription subscription) {
        SubscriptionItem primaryItem = resolvePrimarySubscriptionItem(subscription);
        return ProviderSubscriptionInfo.builder()
                .subscriptionId(subscription.getId())
                .customerId(subscription.getCustomer())
                .status(subscription.getStatus())
                .currentPeriodStart(toLocalDateTime(resolveCurrentPeriodStart(subscription, primaryItem)))
                .currentPeriodEnd(toLocalDateTime(resolveCurrentPeriodEnd(subscription, primaryItem)))
                .cancelAtPeriodEnd(Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()))
                .canceledAt(toLocalDateTime(subscription.getCanceledAt()))
                .build();
    }

    private SubscriptionItem resolvePrimarySubscriptionItem(Subscription subscription) {
        if (subscription == null || subscription.getItems() == null || subscription.getItems().getData() == null
                || subscription.getItems().getData().isEmpty()) {
            return null;
        }
        return subscription.getItems().getData().get(0);
    }

    private Long resolveCurrentPeriodStart(Subscription subscription, SubscriptionItem primaryItem) {
        if (primaryItem != null && primaryItem.getCurrentPeriodStart() != null) {
            return primaryItem.getCurrentPeriodStart();
        }
        return subscription.getBillingCycleAnchor();
    }

    private Long resolveCurrentPeriodEnd(Subscription subscription, SubscriptionItem primaryItem) {
        if (primaryItem != null && primaryItem.getCurrentPeriodEnd() != null) {
            return primaryItem.getCurrentPeriodEnd();
        }
        if (subscription.getCancelAt() != null) {
            return subscription.getCancelAt();
        }
        return subscription.getTrialEnd();
    }

    /**
     * 根据 Session ID 主动查询 Checkout Session 状态
     */
    public Session retrieveCheckoutSession(String sessionId) throws StripeException {
        initStripeApiKey();
        return Session.retrieve(sessionId);
    }

    private void initStripeApiKey() {
        String apiKey = billingProperties.getStripe().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(BillingErrorCode.STRIPE_CONFIG_MISSING, "Stripe API Key 未配置");
        }
        Stripe.apiKey = apiKey;
    }

    private String resolvePlanPriceId(String planCode, String billingCycle) {
        String normalizedPlanCode = planCode == null ? null : planCode.trim().toUpperCase();
        String normalizedCycle = BillingCycle.from(billingCycle).name();
        String currency = billingProperties.getSubscription().getDefaultCurrency();
        if (currency == null || currency.isBlank()) {
            currency = "USD";
        }

        String priceIdFromCatalog = billingPlanPriceMapper.selectActiveStripePriceId(
                PaymentProvider.STRIPE.name(),
                normalizedPlanCode,
                normalizedCycle,
                currency.toUpperCase()
        );
        if (priceIdFromCatalog != null && !priceIdFromCatalog.isBlank()) {
            return priceIdFromCatalog;
        }

        Map<String, Map<String, String>> planPriceIds = billingProperties.getStripe().getPlanPriceIds();
        if (planPriceIds == null || planPriceIds.isEmpty()) {
            return null;
        }

        Map<String, String> cycleMap = planPriceIds.get(planCode);
        if (cycleMap == null) {
            cycleMap = planPriceIds.get(planCode.toLowerCase());
        }
        if (cycleMap == null) {
            cycleMap = planPriceIds.get(planCode.toUpperCase());
        }
        if (cycleMap == null) {
            return null;
        }

        String priceId = cycleMap.get(normalizedCycle);
        if (priceId == null) {
            priceId = cycleMap.get(billingCycle.toUpperCase());
        }
        if (priceId == null) {
            priceId = cycleMap.get(billingCycle.toLowerCase());
        }
        return priceId;
    }

    private String resolveSuccessUrl(String successUrl) {
        if (successUrl != null && !successUrl.isBlank()) {
            return successUrl;
        }
        return billingProperties.getStripe().getDefaultSuccessUrl();
    }

    private String resolveCancelUrl(String cancelUrl) {
        if (cancelUrl != null && !cancelUrl.isBlank()) {
            return cancelUrl;
        }
        return billingProperties.getStripe().getDefaultCancelUrl();
    }

    private String resolveResourceId(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            StripeObject stripeObject = deserializer.getObject().get();
            if (stripeObject instanceof Session session) {
                return session.getId();
            }
            if (stripeObject instanceof Subscription subscription) {
                return subscription.getId();
            }
        }
        return event.getId();
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("UTC"));
    }
}
