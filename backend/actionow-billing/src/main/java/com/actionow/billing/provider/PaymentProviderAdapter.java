package com.actionow.billing.provider;

import com.actionow.billing.entity.PaymentOrder;
import com.actionow.billing.enums.PaymentProvider;

import java.util.Map;

/**
 * 支付渠道适配器 SPI
 */
public interface PaymentProviderAdapter {

    PaymentProvider provider();

    CheckoutSessionResult createTopupCheckoutSession(PaymentOrder order,
                                                     String successUrl,
                                                     String cancelUrl,
                                                     String clientReferenceId) throws Exception;

    CheckoutSessionResult createSubscriptionCheckoutSession(String workspaceId,
                                                            String planCode,
                                                            String billingCycle,
                                                            String successUrl,
                                                            String cancelUrl,
                                                            String clientReferenceId,
                                                            Map<String, String> metadata) throws Exception;

    ProviderWebhookEvent verifyAndParseWebhook(String payload, Map<String, String> headers) throws Exception;

    ProviderSubscriptionInfo retrieveSubscription(String providerSubscriptionId) throws Exception;

    ProviderSubscriptionInfo cancelSubscription(String providerSubscriptionId, boolean cancelAtPeriodEnd) throws Exception;
}
