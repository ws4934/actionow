package com.actionow.billing.service.impl;

import com.actionow.billing.dto.*;
import com.actionow.billing.entity.SubscriptionContract;
import com.actionow.billing.enums.BillingCycle;
import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.billing.enums.SubscriptionStatus;
import com.actionow.billing.feign.WorkspaceFeignClient;
import com.actionow.billing.mapper.SubscriptionContractMapper;
import com.actionow.billing.provider.CheckoutSessionResult;
import com.actionow.billing.provider.PaymentProviderAdapter;
import com.actionow.billing.provider.PaymentProviderRouter;
import com.actionow.billing.provider.ProviderSubscriptionInfo;
import com.actionow.billing.service.SubscriptionBillingService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.Result;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 订阅计费服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBillingServiceImpl implements SubscriptionBillingService {

    private static final String BILLING_SYSTEM_OPERATOR = "billing-system";

    private final SubscriptionContractMapper subscriptionContractMapper;
    private final PaymentProviderRouter providerRouter;
    private final WorkspaceFeignClient workspaceFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChangePlanResponse changePlan(ChangePlanRequest request, String workspaceId, String userId) {
        String targetPlan = normalizePlanCode(request.getTargetPlan());
        PaymentProvider provider = PaymentProvider.from(request.getProvider());
        String billingCycle = BillingCycle.from(request.getBillingCycle()).name();
        String workspacePlanType = resolveWorkspacePlanType(targetPlan);

        // Free 计划：直接降级并取消现有订阅
        if (isFreePlan(targetPlan)) {
            SubscriptionContract active = subscriptionContractMapper.selectActiveByWorkspaceId(workspaceId);
            if (active != null) {
                PaymentProviderAdapter adapter = providerRouter.getAdapter(PaymentProvider.from(active.getProvider()));
                try {
                    adapter.cancelSubscription(active.getProviderSubscriptionId(), false);
                } catch (Exception e) {
                    log.warn("取消 Stripe 订阅失败，继续执行本地降级: workspaceId={}, subscriptionId={}, error={}",
                            workspaceId, active.getProviderSubscriptionId(), e.getMessage());
                }
                active.setStatus(SubscriptionStatus.CANCELED.name());
                active.setCanceledAt(LocalDateTime.now());
                active.setUpdatedAt(LocalDateTime.now());
                subscriptionContractMapper.updateById(active);
            }

            syncWorkspacePlan(workspaceId, workspacePlanType, userId);

            return ChangePlanResponse.builder()
                    .workspaceId(workspaceId)
                    .oldPlan(active != null ? active.getPlanCode() : null)
                    .targetPlan(targetPlan)
                    .status("ACTIVE")
                    .effectiveAt(LocalDateTime.now())
                    .build();
        }

        PaymentProviderAdapter adapter = providerRouter.getAdapter(provider);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("workspaceId", workspaceId);
        metadata.put("targetPlan", targetPlan);
        metadata.put("billingCycle", billingCycle);
        metadata.put("requestedBy", userId);

        CheckoutSessionResult sessionResult;
        try {
            sessionResult = adapter.createSubscriptionCheckoutSession(
                    workspaceId,
                    targetPlan,
                    billingCycle,
                    null,
                    null,
                    workspaceId,
                    metadata
            );
        } catch (Exception e) {
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID,
                    "创建订阅 Checkout Session 失败: " + e.getMessage());
        }

        return ChangePlanResponse.builder()
                .workspaceId(workspaceId)
                .targetPlan(targetPlan)
                .subscriptionId(sessionResult.getPaymentId())
                .status("PENDING")
                .checkoutUrl(sessionResult.getCheckoutUrl())
                .effectiveAt(sessionResult.getExpiresAt())
                .build();
    }

    @Override
    public SubscriptionResponse getCurrentSubscription(String workspaceId) {
        SubscriptionContract contract = subscriptionContractMapper.selectActiveByWorkspaceId(workspaceId);
        if (contract == null) {
            return null;
        }
        return toResponse(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse cancelSubscription(String subscriptionId,
                                                   CancelSubscriptionRequest request,
                                                   String workspaceId,
                                                   String userId) {
        SubscriptionContract contract = subscriptionContractMapper.selectByContractId(subscriptionId);
        if (contract == null || !workspaceId.equals(contract.getWorkspaceId())) {
            throw new BusinessException(BillingErrorCode.SUBSCRIPTION_NOT_FOUND);
        }

        PaymentProviderAdapter adapter = providerRouter.getAdapter(PaymentProvider.from(contract.getProvider()));

        ProviderSubscriptionInfo info;
        try {
            info = adapter.cancelSubscription(contract.getProviderSubscriptionId(),
                    request == null || request.getCancelAtPeriodEnd() == null || request.getCancelAtPeriodEnd());
        } catch (Exception e) {
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID,
                    "取消 Stripe 订阅失败: " + e.getMessage());
        }

        contract.setStatus(mapStripeSubscriptionStatus(info.getStatus()));
        contract.setCancelAtPeriodEnd(info.getCancelAtPeriodEnd());
        contract.setCanceledAt(info.getCanceledAt());
        contract.setCurrentPeriodStart(info.getCurrentPeriodStart());
        contract.setCurrentPeriodEnd(info.getCurrentPeriodEnd());
        contract.setUpdatedAt(LocalDateTime.now());
        subscriptionContractMapper.updateById(contract);

        if (SubscriptionStatus.CANCELED.name().equals(contract.getStatus()) ||
                SubscriptionStatus.EXPIRED.name().equals(contract.getStatus())) {
            syncWorkspacePlan(workspaceId, "Free", userId);
        }

        return toResponse(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleStripeSubscriptionCheckoutCompleted(Session session) {
        if (session == null || session.getMetadata() == null) {
            return;
        }

        String workspaceId = session.getMetadata().get("workspaceId");
        String targetPlan = normalizePlanCode(session.getMetadata().get("targetPlan"));
        String billingCycle = session.getMetadata().get("billingCycle");
        // requestedBy 在 changePlan 时写入 metadata，clientReferenceId 是 workspaceId 不是 userId
        String userId = session.getMetadata().get("requestedBy");
        String providerSubscriptionId = session.getSubscription();

        if (workspaceId == null || targetPlan == null || providerSubscriptionId == null) {
            log.warn("订阅 Checkout 回调缺少关键字段，跳过: sessionId={}", session.getId());
            return;
        }

        PaymentProviderAdapter adapter = providerRouter.getAdapter(PaymentProvider.STRIPE);

        ProviderSubscriptionInfo info;
        try {
            info = adapter.retrieveSubscription(providerSubscriptionId);
        } catch (Exception e) {
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID,
                    "拉取 Stripe 订阅详情失败: " + e.getMessage());
        }

        upsertSubscriptionContract(
                workspaceId,
                userId,
                PaymentProvider.STRIPE.name(),
                targetPlan,
                billingCycle,
                info
        );

        syncWorkspacePlan(workspaceId, resolveWorkspacePlanType(targetPlan), BILLING_SYSTEM_OPERATOR);

        log.info("订阅 Checkout 完成并同步计划成功: workspaceId={}, subscriptionId={}, plan={}, workspacePlanType={}",
                workspaceId, providerSubscriptionId, targetPlan, resolveWorkspacePlanType(targetPlan));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleStripeSubscriptionUpdated(Subscription subscription) {
        if (subscription == null) {
            return;
        }

        SubscriptionContract contract = subscriptionContractMapper.selectByProviderSubscriptionId(
                PaymentProvider.STRIPE.name(), subscription.getId());
        if (contract == null) {
            log.warn("收到 subscription.updated 但本地无记录: subscriptionId={}", subscription.getId());
            return;
        }

        SubscriptionItem primaryItem = resolvePrimarySubscriptionItem(subscription);
        String newStatus = mapStripeSubscriptionStatus(subscription.getStatus());
        validateStatusTransition(contract.getStatus(), newStatus, subscription.getId());
        contract.setStatus(newStatus);
        contract.setCurrentPeriodStart(toLocalDateTime(resolveCurrentPeriodStart(subscription, primaryItem)));
        contract.setCurrentPeriodEnd(toLocalDateTime(resolveCurrentPeriodEnd(subscription, primaryItem)));
        contract.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
        contract.setCanceledAt(toLocalDateTime(subscription.getCanceledAt()));
        contract.setUpdatedAt(LocalDateTime.now());
        subscriptionContractMapper.updateById(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleStripeSubscriptionDeleted(Subscription subscription) {
        if (subscription == null) {
            return;
        }

        SubscriptionContract contract = subscriptionContractMapper.selectByProviderSubscriptionId(
                PaymentProvider.STRIPE.name(), subscription.getId());
        if (contract == null) {
            return;
        }

        validateStatusTransition(contract.getStatus(), SubscriptionStatus.CANCELED.name(), subscription.getId());
        contract.setStatus(SubscriptionStatus.CANCELED.name());
        contract.setCancelAtPeriodEnd(Boolean.TRUE);
        contract.setCanceledAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        subscriptionContractMapper.updateById(contract);

        syncWorkspacePlan(contract.getWorkspaceId(), "Free", BILLING_SYSTEM_OPERATOR);
    }

    private void upsertSubscriptionContract(String workspaceId,
                                            String userId,
                                            String provider,
                                            String targetPlan,
                                            String billingCycle,
                                            ProviderSubscriptionInfo info) {
        // 用第一次查询结果直接决策，避免双查询导致的 TOCTOU 竞态
        SubscriptionContract existing = subscriptionContractMapper.selectByProviderSubscriptionId(provider, info.getSubscriptionId());

        SubscriptionContract contract;
        if (existing == null) {
            contract = new SubscriptionContract();
            contract.setId(UuidGenerator.generateUuidV7());
            contract.setWorkspaceId(workspaceId);
            contract.setUserId(userId);
            contract.setProvider(provider);
            contract.setProviderSubscriptionId(info.getSubscriptionId());
            contract.setCreatedAt(LocalDateTime.now());
            contract.setVersion(0);
        } else {
            // 复用已有记录的 id/version，确保 updateById 命中正确行
            contract = existing;
        }

        contract.setProviderCustomerId(info.getCustomerId());
        contract.setPlanCode(targetPlan);
        contract.setBillingCycle(billingCycle);
        contract.setStatus(mapStripeSubscriptionStatus(info.getStatus()));
        contract.setCurrentPeriodStart(info.getCurrentPeriodStart());
        contract.setCurrentPeriodEnd(info.getCurrentPeriodEnd());
        contract.setCancelAtPeriodEnd(info.getCancelAtPeriodEnd());
        contract.setCanceledAt(info.getCanceledAt());
        contract.setUpdatedAt(LocalDateTime.now());

        if (existing == null) {
            subscriptionContractMapper.insert(contract);
        } else {
            subscriptionContractMapper.updateById(contract);
        }
    }

    private void syncWorkspacePlan(String workspaceId, String planType, String operatorId) {
        Result<Void> result = workspaceFeignClient.updatePlanInternal(workspaceId, planType, operatorId);
        if (result == null || !result.isSuccess()) {
            String message = result != null ? result.getMessage() : "workspace 服务无响应";
            throw new BusinessException(BillingErrorCode.WORKSPACE_PLAN_SYNC_FAILED, message);
        }
    }

    private SubscriptionResponse toResponse(SubscriptionContract contract) {
        return SubscriptionResponse.builder()
                .subscriptionId(contract.getId())
                .workspaceId(contract.getWorkspaceId())
                .provider(contract.getProvider())
                .planCode(contract.getPlanCode())
                .billingCycle(contract.getBillingCycle())
                .status(contract.getStatus())
                .currentPeriodStart(contract.getCurrentPeriodStart())
                .currentPeriodEnd(contract.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(contract.getCancelAtPeriodEnd())
                .gracePeriodEnd(contract.getGracePeriodEnd())
                .build();
    }

    private String mapStripeSubscriptionStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return SubscriptionStatus.ACTIVE.name();
        }
        return switch (stripeStatus) {
            case "trialing" -> SubscriptionStatus.TRIALING.name();
            case "active" -> SubscriptionStatus.ACTIVE.name();
            // "incomplete": 首次付款尚未成功（最多 23h 补缴），不应给予正常权益
            // "past_due":   续费失败，宽限期内
            // "unpaid":     宽限期已过，账单未付
            // "paused":     订阅已主动暂停
            case "incomplete", "past_due", "unpaid", "paused" -> SubscriptionStatus.PAST_DUE.name();
            case "canceled" -> SubscriptionStatus.CANCELED.name();
            case "incomplete_expired" -> SubscriptionStatus.EXPIRED.name();
            default -> {
                log.warn("未知的 Stripe 订阅状态，降级为 PAST_DUE: stripeStatus={}", stripeStatus);
                yield SubscriptionStatus.PAST_DUE.name();
            }
        };
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
     * 校验订阅状态转换是否合法。
     *
     * 策略：
     * 1. 目标状态与当前一致 → 直接放行（幂等 Webhook 重发）。
     * 2. 当前是终态（EXPIRED）且目标 ≠ 当前 → BLOCK 并抛业务异常。
     *    原因：终态一旦被非法覆盖会带来账务漂移，且 EXPIRED 的订阅不应再被 Stripe
     *    通过 updated/deleted Webhook 唤醒；此时拒绝更新比写脏数据更安全。
     * 3. 其他非法转换（非终态 → 非法目标） → 仅 WARN，允许执行。
     *    原因：Webhook 乱序/丢失场景下本地状态可能落后，WARN 留线索但不能挡住合法更新。
     */
    private void validateStatusTransition(String currentStatusStr, String newStatusStr, String subscriptionId) {
        SubscriptionStatus current = SubscriptionStatus.from(currentStatusStr);
        SubscriptionStatus target = SubscriptionStatus.from(newStatusStr);
        if (current == null || target == null || current == target) {
            return;
        }
        if (current.canTransitionTo(target)) {
            return;
        }
        if (current.isTerminal()) {
            log.error("拒绝从终态覆盖订阅状态: {} → {}，subscriptionId={}",
                    currentStatusStr, newStatusStr, subscriptionId);
            throw new BusinessException(BillingErrorCode.SUBSCRIPTION_STATUS_INVALID,
                    "订阅已处于终态 " + currentStatusStr + "，不允许转换到 " + newStatusStr);
        }
        log.warn("订阅状态转换异常: {} → {}，subscriptionId={}（仍将执行更新，可能因中间 Webhook 丢失）",
                currentStatusStr, newStatusStr, subscriptionId);
    }

    private boolean isFreePlan(String planCode) {
        return "FREE".equalsIgnoreCase(planCode);
    }

    private String normalizePlanCode(String planCode) {
        if (planCode == null) {
            return null;
        }
        return planCode.trim().toUpperCase();
    }

    private String resolveWorkspacePlanType(String planCode) {
        String normalized = normalizePlanCode(planCode);
        if (normalized == null) {
            return "Free";
        }
        return switch (normalized) {
            case "FREE" -> "Free";
            case "BASIC" -> "Basic";
            case "PRO" -> "Pro";
            case "TEAM", "TEAMM", "ENTERPRISE" -> "Enterprise";
            default -> normalized;
        };
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return LocalDateTime.ofEpochSecond(epochSeconds, 0, java.time.ZoneOffset.UTC);
    }
}
