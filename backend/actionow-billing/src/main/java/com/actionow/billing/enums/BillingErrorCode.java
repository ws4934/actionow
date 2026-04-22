package com.actionow.billing.enums;

import com.actionow.common.core.result.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计费服务错误码
 * 403xx: 计费与支付相关
 */
@Getter
@AllArgsConstructor
public enum BillingErrorCode implements IResultCode {

    ORDER_NOT_FOUND("40301", "支付订单不存在"),
    ORDER_STATUS_INVALID("40302", "支付订单状态不允许当前操作"),
    PROVIDER_NOT_SUPPORTED("40303", "不支持的支付渠道"),
    STRIPE_CONFIG_MISSING("40304", "Stripe 配置缺失"),
    STRIPE_WEBHOOK_VERIFY_FAILED("40305", "Stripe 回调验签失败"),
    CALLBACK_EVENT_INVALID("40306", "支付回调事件无效"),
    WALLET_TOPUP_FAILED("40307", "钱包入账失败"),
    WORKSPACE_PLAN_SYNC_FAILED("40308", "工作空间计划同步失败"),
    PLAN_PRICE_NOT_CONFIGURED("40309", "订阅价格未配置"),
    SUBSCRIPTION_NOT_FOUND("40310", "订阅不存在"),
    WECHATPAY_CONFIG_MISSING("40311", "微信支付配置缺失"),
    WECHATPAY_WEBHOOK_VERIFY_FAILED("40312", "微信支付回调验签失败"),
    WECHATPAY_ORDER_FAILED("40313", "微信支付下单失败"),
    CURRENCY_NOT_SUPPORTED("40314", "不支持的币种"),
    SUBSCRIPTION_STATUS_INVALID("40315", "订阅状态不允许当前操作");

    private final String code;
    private final String message;
}
