package com.actionow.billing.provider.wechatpay;

import com.actionow.billing.config.BillingProperties;
import com.actionow.billing.entity.PaymentOrder;
import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.billing.provider.CheckoutSessionResult;
import com.actionow.billing.provider.PaymentProviderAdapter;
import com.actionow.billing.provider.ProviderSubscriptionInfo;
import com.actionow.billing.provider.ProviderWebhookEvent;
import com.actionow.common.core.exception.BusinessException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 微信支付 Native 扫码支付适配器
 * 仅支持充值（Topup），不支持订阅
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatPayProviderAdapter implements PaymentProviderAdapter {

    private static final String WECHATPAY_SERIAL_HEADER = "Wechatpay-Serial";
    private static final String WECHATPAY_SIGNATURE_HEADER = "Wechatpay-Signature";
    private static final String WECHATPAY_TIMESTAMP_HEADER = "Wechatpay-Timestamp";
    private static final String WECHATPAY_NONCE_HEADER = "Wechatpay-Nonce";

    private final BillingProperties billingProperties;
    private final WechatPayClientFactory clientFactory;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.WECHATPAY;
    }

    @Override
    public CheckoutSessionResult createTopupCheckoutSession(PaymentOrder order,
                                                            String successUrl,
                                                            String cancelUrl,
                                                            String clientReferenceId) throws Exception {
        NativePayService nativePayService = clientFactory.getNativePayService();

        PrepayRequest request = new PrepayRequest();
        request.setAppid(billingProperties.getWechatpay().getAppId());
        request.setMchid(billingProperties.getWechatpay().getMchId());
        request.setDescription("Actionow 积分充值");
        request.setOutTradeNo(order.getOrderNo());
        String notifyUrl = billingProperties.getWechatpay().getNotifyUrl();
        if (notifyUrl == null || notifyUrl.isBlank()) {
            // notify_url 是微信支付必填字段，未配置时使用占位地址（由轮询补偿确认支付结果）
            notifyUrl = "https://localhost/billing/callback/WECHATPAY";
        }
        request.setNotifyUrl(notifyUrl);

        Amount amount = new Amount();
        long amountMinor = order.getAmountMinor();
        if (amountMinor > Integer.MAX_VALUE) {
            throw new BusinessException(BillingErrorCode.WECHATPAY_ORDER_FAILED,
                    "充值金额超出微信支付限额: " + amountMinor);
        }
        amount.setTotal((int) amountMinor);
        amount.setCurrency("CNY");
        request.setAmount(amount);

        PrepayResponse response;
        try {
            response = nativePayService.prepay(request);
        } catch (Exception e) {
            log.error("微信支付 Native 下单失败: orderNo={}, error={}", order.getOrderNo(), e.getMessage());
            throw new BusinessException(BillingErrorCode.WECHATPAY_ORDER_FAILED,
                    "微信支付下单失败: " + e.getMessage());
        }

        log.info("微信支付 Native 下单成功: orderNo={}", order.getOrderNo());

        return CheckoutSessionResult.builder()
                .sessionId(order.getOrderNo())
                .checkoutUrl(response.getCodeUrl())
                .paymentId(null)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();
    }

    @Override
    public CheckoutSessionResult createSubscriptionCheckoutSession(String workspaceId,
                                                                   String planCode,
                                                                   String billingCycle,
                                                                   String successUrl,
                                                                   String cancelUrl,
                                                                   String clientReferenceId,
                                                                   Map<String, String> metadata) {
        throw new UnsupportedOperationException("微信支付不支持订阅");
    }

    @Override
    public ProviderWebhookEvent verifyAndParseWebhook(String payload, Map<String, String> headers) {
        NotificationParser parser = clientFactory.getNotificationParser();

        String serial = resolveHeader(headers, WECHATPAY_SERIAL_HEADER);
        String signature = resolveHeader(headers, WECHATPAY_SIGNATURE_HEADER);
        String timestamp = resolveHeader(headers, WECHATPAY_TIMESTAMP_HEADER);
        String nonce = resolveHeader(headers, WECHATPAY_NONCE_HEADER);

        if (serial == null || signature == null || timestamp == null || nonce == null) {
            throw new BusinessException(BillingErrorCode.WECHATPAY_WEBHOOK_VERIFY_FAILED,
                    "缺少微信支付回调签名头");
        }

        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(payload)
                .build();

        Transaction transaction;
        try {
            transaction = parser.parse(requestParam, Transaction.class);
        } catch (Exception e) {
            log.error("微信支付回调验签/解密失败: {}", e.getMessage());
            throw new BusinessException(BillingErrorCode.WECHATPAY_WEBHOOK_VERIFY_FAILED,
                    "微信支付回调验签失败: " + e.getMessage());
        }

        String eventType = mapTradeStateToEventType(transaction.getTradeState());

        return ProviderWebhookEvent.builder()
                .provider(PaymentProvider.WECHATPAY.name())
                .eventId(transaction.getOutTradeNo() + ":" + transaction.getTradeState())
                .eventType(eventType)
                .resourceId(transaction.getTransactionId())
                .rawEvent(transaction)
                .build();
    }

    @Override
    public ProviderSubscriptionInfo retrieveSubscription(String providerSubscriptionId) {
        throw new UnsupportedOperationException("微信支付不支持订阅");
    }

    @Override
    public ProviderSubscriptionInfo cancelSubscription(String providerSubscriptionId, boolean cancelAtPeriodEnd) {
        throw new UnsupportedOperationException("微信支付不支持订阅");
    }

    /**
     * 查询订单状态（供轮询任务使用）
     */
    public Transaction queryOrder(String outTradeNo) {
        try {
            com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest request =
                    new com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest();
            request.setMchid(billingProperties.getWechatpay().getMchId());
            request.setOutTradeNo(outTradeNo);
            return clientFactory.getNativePayService().queryOrderByOutTradeNo(request);
        } catch (Exception e) {
            log.warn("微信支付查单失败: outTradeNo={}, error={}", outTradeNo, e.getMessage());
            return null;
        }
    }

    /**
     * 关闭订单（超时未支付时调用）
     */
    public void closeOrder(String outTradeNo) {
        try {
            com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest request =
                    new com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest();
            request.setMchid(billingProperties.getWechatpay().getMchId());
            request.setOutTradeNo(outTradeNo);
            clientFactory.getNativePayService().closeOrder(request);
            log.info("微信支付关单成功: outTradeNo={}", outTradeNo);
        } catch (Exception e) {
            log.warn("微信支付关单失败: outTradeNo={}, error={}", outTradeNo, e.getMessage());
        }
    }

    private String mapTradeStateToEventType(Transaction.TradeStateEnum tradeState) {
        if (tradeState == null) {
            return "UNKNOWN";
        }
        return switch (tradeState) {
            case SUCCESS -> "TRANSACTION.SUCCESS";
            case CLOSED -> "TRANSACTION.CLOSED";
            case PAYERROR -> "TRANSACTION.PAYERROR";
            case REVOKED -> "TRANSACTION.REVOKED";
            case REFUND -> "TRANSACTION.REFUND";
            default -> "TRANSACTION." + tradeState.name();
        };
    }

    /**
     * 从 headers 中获取值（兼容大小写）
     */
    private String resolveHeader(Map<String, String> headers, String name) {
        String value = headers.get(name);
        if (value == null) {
            value = headers.get(name.toLowerCase());
        }
        return value;
    }
}
