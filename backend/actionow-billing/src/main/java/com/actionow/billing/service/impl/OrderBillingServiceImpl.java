package com.actionow.billing.service.impl;

import com.actionow.billing.dto.*;
import com.actionow.billing.config.BillingProperties;
import com.actionow.billing.config.BillingRuntimeConfigService;
import com.actionow.billing.entity.PaymentOrder;
import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.billing.enums.OrderStatus;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.billing.feign.SystemFeignClient;
import com.actionow.billing.feign.WalletFeignClient;
import com.actionow.billing.feign.WalletTopupRequest;
import com.actionow.billing.mapper.PaymentOrderMapper;
import com.actionow.billing.provider.CheckoutSessionResult;
import com.actionow.billing.provider.PaymentProviderAdapter;
import com.actionow.billing.provider.PaymentProviderRouter;
import com.actionow.billing.provider.stripe.StripePaymentProviderAdapter;
import com.actionow.billing.provider.wechatpay.WechatPayProviderAdapter;
import com.actionow.billing.service.OrderBillingService;
import com.stripe.model.checkout.Session;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单计费服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBillingServiceImpl implements OrderBillingService {

    private static final String ORDER_TYPE_TOPUP = "TOPUP";
    private static final String BILLING_SYSTEM_OPERATOR = "billing-system";
    private static final String TOPUP_RATE_CONFIG_PREFIX = "billing.topup.rate.";
    private static final long RATE_CACHE_TTL_MS = 5 * 60 * 1000L;

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentProviderRouter providerRouter;
    private final WalletFeignClient walletFeignClient;
    private final SystemFeignClient systemFeignClient;
    private final BillingProperties billingProperties;
    private final BillingRuntimeConfigService billingRuntimeConfig;
    private final ObjectMapper objectMapper;

    /** 本地缓存: currency -> {rate, expireAt} */
    private final Map<String, CachedRate> rateCacheMap = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateTopupOrderResponse createTopupOrder(CreateTopupOrderRequest request, String workspaceId, String userId) {
        PaymentProvider provider = PaymentProvider.from(request.getProvider());

        if (request.getAmountMinor() == null || request.getAmountMinor() <= 0) {
            throw new BusinessException(BillingErrorCode.ORDER_STATUS_INVALID, "充值金额必须大于 0");
        }

        // 币种白名单通过 BillingRuntimeConfigService 动态下发（Redis），
        // 运营调整新币种不需发版；枚举仍保留作为内部代码约束。
        if (!billingRuntimeConfig.isCurrencySupported(request.getCurrency())) {
            throw new BusinessException(BillingErrorCode.CURRENCY_NOT_SUPPORTED,
                    "不支持的币种: " + request.getCurrency());
        }

        PaymentOrder order = new PaymentOrder();
        order.setId(UuidGenerator.generateUuidV7());
        order.setOrderNo(generateOrderNo());
        order.setWorkspaceId(workspaceId);
        order.setUserId(userId);
        order.setProvider(provider.name());
        order.setOrderType(ORDER_TYPE_TOPUP);
        order.setAmountMinor(request.getAmountMinor());
        order.setCurrency(request.getCurrency().toUpperCase());
        order.setPointsAmount(resolvePointsAmount(request));
        order.setStatus(OrderStatus.INIT.name());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setVersion(0);

        Map<String, Object> meta = new HashMap<>();
        meta.put("description", request.getDescription());
        meta.put("paymentMethod", request.getPaymentMethod());
        order.setMeta(meta);

        paymentOrderMapper.insert(order);

        return CreateTopupOrderResponse.builder()
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .workspaceId(order.getWorkspaceId())
                .amountMinor(order.getAmountMinor())
                .currency(order.getCurrency())
                .provider(order.getProvider())
                .pointsAmount(order.getPointsAmount())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateCheckoutSessionResponse createTopupCheckoutSession(String orderNo,
                                                                    CreateCheckoutSessionRequest request,
                                                                    String workspaceId) {
        PaymentOrder order = getOrderOrThrow(orderNo);
        assertWorkspaceMatch(order, workspaceId);

        if (!OrderStatus.INIT.name().equals(order.getStatus()) && !OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException(BillingErrorCode.ORDER_STATUS_INVALID,
                    "订单状态不允许创建支付会话: " + order.getStatus());
        }

        PaymentProviderAdapter adapter = providerRouter.getAdapter(PaymentProvider.from(order.getProvider()));

        CheckoutSessionResult result;
        try {
            result = adapter.createTopupCheckoutSession(order,
                    request.getSuccessUrl(),
                    request.getCancelUrl(),
                    request.getClientReferenceId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID,
                    "创建支付会话失败: " + e.getMessage());
        }

        paymentOrderMapper.markPendingWithSession(orderNo, result.getSessionId());

        return CreateCheckoutSessionResponse.builder()
                .orderNo(orderNo)
                .providerSessionId(result.getSessionId())
                .checkoutUrl(result.getCheckoutUrl())
                .expiresAt(result.getExpiresAt())
                .build();
    }

    @Override
    public OrderDetailResponse getOrder(String orderNo, String workspaceId) {
        PaymentOrder order = getOrderOrThrow(orderNo);
        assertWorkspaceMatch(order, workspaceId);

        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .workspaceId(order.getWorkspaceId())
                .userId(order.getUserId())
                .provider(order.getProvider())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .amountMinor(order.getAmountMinor())
                .currency(order.getCurrency())
                .pointsAmount(order.getPointsAmount())
                .providerPaymentId(order.getProviderPaymentId())
                .providerSessionId(order.getProviderSessionId())
                .failCode(order.getFailCode())
                .failMessage(order.getFailMessage())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .meta(order.getMeta())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTopupPaid(String orderNo,
                                String providerPaymentId,
                                String providerSessionId,
                                String provider) {
        PaymentOrder order = getOrderOrThrow(orderNo);

        if (!ORDER_TYPE_TOPUP.equals(order.getOrderType())) {
            return;
        }

        // 幂等保护：订单已支付则跳过，防止 Webhook 重放或事务重试导致重复充值
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            log.info("订单已支付，幂等跳过钱包入账: orderNo={}", orderNo);
            return;
        }

        // CAS 式更新：WHERE status <> 'PAID'，返回 0 表示另一并发调用已完成
        int markUpdated = paymentOrderMapper.markPaid(orderNo, providerPaymentId, providerSessionId);
        if (markUpdated == 0) {
            log.warn("订单 markPaid 无行变更，跳过钱包入账（并发已处理）: orderNo={}", orderNo);
            return;
        }

        // 调用钱包入账。wallet 侧会基于 paymentOrderId 做幂等。
        WalletTopupRequest topupRequest = new WalletTopupRequest();
        topupRequest.setAmount(order.getPointsAmount());
        topupRequest.setDescription("支付充值: " + orderNo);
        topupRequest.setPaymentOrderId(orderNo);
        topupRequest.setPaymentMethod(provider);

        Result<Object> topupResult = walletFeignClient.topup(order.getWorkspaceId(), topupRequest, BILLING_SYSTEM_OPERATOR);
        if (topupResult == null || !topupResult.isSuccess()) {
            String error = topupResult != null ? topupResult.getMessage() : "wallet 服务无响应";
            log.error("支付成功但钱包入账失败: orderNo={}, workspaceId={}, error={}",
                    orderNo, order.getWorkspaceId(), error);
            throw new BusinessException(BillingErrorCode.WALLET_TOPUP_FAILED, error);
        }

        log.info("充值处理成功: orderNo={}, provider={}, workspaceId={}, amount={}",
                orderNo, provider, order.getWorkspaceId(), order.getPointsAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTopupFailed(String orderNo, String failCode, String failMessage) {
        paymentOrderMapper.markFailed(orderNo, failCode, failMessage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailResponse verifyPayment(String orderNo, String workspaceId) {
        PaymentOrder order = getOrderOrThrow(orderNo);
        assertWorkspaceMatch(order, workspaceId);

        // 已支付或已终态，直接返回
        if (OrderStatus.PAID.name().equals(order.getStatus())
                || OrderStatus.FAILED.name().equals(order.getStatus())
                || OrderStatus.CANCELED.name().equals(order.getStatus())
                || OrderStatus.EXPIRED.name().equals(order.getStatus())) {
            return getOrder(orderNo, workspaceId);
        }

        PaymentProvider provider = PaymentProvider.from(order.getProvider());
        PaymentProviderAdapter adapter = providerRouter.getAdapter(provider);

        try {
            switch (provider) {
                case STRIPE -> verifyStripePayment(order, (StripePaymentProviderAdapter) adapter);
                case WECHATPAY -> verifyWechatPayment(order, (WechatPayProviderAdapter) adapter);
                default -> log.warn("verifyPayment 不支持的渠道: {}", provider);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("主动查询支付状态失败: orderNo={}, provider={}, error={}",
                    orderNo, provider, e.getMessage());
        }

        return getOrder(orderNo, workspaceId);
    }

    private void verifyStripePayment(PaymentOrder order, StripePaymentProviderAdapter adapter) throws Exception {
        String sessionId = order.getProviderSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("订单无 providerSessionId，无法查询 Stripe: orderNo={}", order.getOrderNo());
            return;
        }

        Session session = adapter.retrieveCheckoutSession(sessionId);
        if ("paid".equals(session.getPaymentStatus())) {
            handleTopupPaid(order.getOrderNo(),
                    session.getPaymentIntent(),
                    session.getId(),
                    PaymentProvider.STRIPE.name());
        }
    }

    private void verifyWechatPayment(PaymentOrder order, WechatPayProviderAdapter adapter) {
        Transaction transaction = adapter.queryOrder(order.getOrderNo());
        if (transaction == null) {
            return;
        }
        if (transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
            handleTopupPaid(order.getOrderNo(),
                    transaction.getTransactionId(),
                    null,
                    PaymentProvider.WECHATPAY.name());
        } else if (transaction.getTradeState() == Transaction.TradeStateEnum.CLOSED
                || transaction.getTradeState() == Transaction.TradeStateEnum.PAYERROR) {
            handleTopupFailed(order.getOrderNo(),
                    transaction.getTradeState().name(),
                    "微信支付失败");
        }
    }

    private PaymentOrder getOrderOrThrow(String orderNo) {
        PaymentOrder order = paymentOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(BillingErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void assertWorkspaceMatch(PaymentOrder order, String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(BillingErrorCode.ORDER_STATUS_INVALID, "workspaceId 不能为空");
        }
        if (!workspaceId.equals(order.getWorkspaceId())) {
            throw new BusinessException(BillingErrorCode.ORDER_STATUS_INVALID, "订单不属于当前工作空间");
        }
    }

    private String generateOrderNo() {
        return "TOPUP_" + UuidGenerator.generateShortId().substring(0, 24).toUpperCase();
    }

    private Long resolvePointsAmount(CreateTopupOrderRequest request) {
        if (request.getPointsAmount() != null && request.getPointsAmount() > 0) {
            return request.getPointsAmount();
        }

        String currency = request.getCurrency() != null ? request.getCurrency().toUpperCase() : "USD";
        long pointsPerMajorUnit;
        long minorPerMajorUnit;

        // 优先从 system 动态配置获取汇率
        long[] rate = fetchTopupRate(currency);
        if (rate != null) {
            pointsPerMajorUnit = rate[0];
            minorPerMajorUnit = rate[1];
        } else {
            // 降级使用运行时动态配置（含 Redis 缓存 + 硬编码兜底）
            pointsPerMajorUnit = Math.max(1L, billingRuntimeConfig.getPointsPerMajorUnit());
            minorPerMajorUnit = Math.max(1L, billingRuntimeConfig.getMinorPerMajorUnit());
        }

        long amountMinor = request.getAmountMinor();
        long points = (amountMinor * pointsPerMajorUnit) / minorPerMajorUnit;
        return Math.max(1L, points);
    }

    /**
     * 从 system 服务获取积分汇率（带本地缓存）
     *
     * @return [pointsPerMajorUnit, minorPerMajorUnit], 或 null 表示不可用
     */
    private long[] fetchTopupRate(String currency) {
        CachedRate cached = rateCacheMap.get(currency);
        if (cached != null && System.currentTimeMillis() < cached.expireAt) {
            return cached.rate;
        }

        try {
            Result<String> result = systemFeignClient.getConfigValue(TOPUP_RATE_CONFIG_PREFIX + currency);
            if (result != null && result.isSuccess() && result.getData() != null) {
                JsonNode node = objectMapper.readTree(result.getData());
                long pointsPerMajorUnit = node.path("pointsPerMajorUnit").asLong(10L);
                long minorPerMajorUnit = node.path("minorPerMajorUnit").asLong(100L);
                long[] rate = {Math.max(1L, pointsPerMajorUnit), Math.max(1L, minorPerMajorUnit)};
                rateCacheMap.put(currency, new CachedRate(rate, System.currentTimeMillis() + RATE_CACHE_TTL_MS));
                return rate;
            }
        } catch (Exception e) {
            log.warn("获取积分汇率失败, 使用默认配置: currency={}, error={}", currency, e.getMessage());
        }

        return null;
    }

    @Override
    public TopupRateResponse getTopupRate(String currency) {
        String normalized = (currency != null && !currency.isBlank()) ? currency.toUpperCase() : "USD";

        long[] rate = fetchTopupRate(normalized);
        long pointsPerMajorUnit;
        long minorPerMajorUnit;

        if (rate != null) {
            pointsPerMajorUnit = rate[0];
            minorPerMajorUnit = rate[1];
        } else {
            pointsPerMajorUnit = Math.max(1L, billingProperties.getTopup().getPointsPerMajorUnit());
            minorPerMajorUnit = Math.max(1L, billingProperties.getTopup().getMinorPerMajorUnit());
        }

        return TopupRateResponse.builder()
                .currency(normalized)
                .pointsPerMajorUnit(pointsPerMajorUnit)
                .minorPerMajorUnit(minorPerMajorUnit)
                .build();
    }

    private record CachedRate(long[] rate, long expireAt) {}
}
