package com.actionow.billing.enums;

/**
 * 支付渠道
 */
public enum PaymentProvider {
    STRIPE,
    /**
     * 支付宝（当前未实现 Adapter，调用将抛出 PROVIDER_NOT_SUPPORTED）
     */
    @Deprecated
    ALIPAY,
    WECHATPAY;

    public static PaymentProvider from(String value) {
        for (PaymentProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported payment provider: " + value);
    }

    /**
     * 判断当前渠道是否已实现 Adapter
     */
    public boolean isImplemented() {
        return this == STRIPE || this == WECHATPAY;
    }
}
