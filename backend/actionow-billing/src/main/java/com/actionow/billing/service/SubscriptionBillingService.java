package com.actionow.billing.service;

import com.actionow.billing.dto.*;
import com.stripe.model.checkout.Session;

public interface SubscriptionBillingService {

    ChangePlanResponse changePlan(ChangePlanRequest request, String workspaceId, String userId);

    SubscriptionResponse getCurrentSubscription(String workspaceId);

    SubscriptionResponse cancelSubscription(String subscriptionId,
                                            CancelSubscriptionRequest request,
                                            String workspaceId,
                                            String userId);

    void handleStripeSubscriptionCheckoutCompleted(Session session);

    void handleStripeSubscriptionUpdated(com.stripe.model.Subscription subscription);

    void handleStripeSubscriptionDeleted(com.stripe.model.Subscription subscription);
}
