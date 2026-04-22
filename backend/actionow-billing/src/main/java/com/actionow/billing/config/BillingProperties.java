package com.actionow.billing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 计费配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "actionow.billing")
public class BillingProperties {

    private Stripe stripe = new Stripe();
    private WechatPay wechatpay = new WechatPay();
    private Topup topup = new Topup();
    private Subscription subscription = new Subscription();

    @Data
    public static class Stripe {
        /** Stripe Secret Key */
        private String apiKey;
        /** Stripe Webhook Secret */
        private String webhookSecret;
        /** 默认支付成功跳转 */
        private String defaultSuccessUrl;
        /** 默认支付取消跳转 */
        private String defaultCancelUrl;
        /** 订阅价格映射: plan -> cycle -> priceId */
        private Map<String, Map<String, String>> planPriceIds = new HashMap<>();
    }

    @Data
    public static class WechatPay {
        /** 微信支付 AppID */
        private String appId;
        /** 商户号 */
        private String mchId;
        /** APIv3 密钥 */
        private String apiV3Key;
        /** 商户私钥文件路径 */
        private String privateKeyPath;
        /** 商户证书序列号 */
        private String mchSerialNumber;
        /** 支付结果回调地址 */
        private String notifyUrl;
    }

    @Data
    public static class Topup {
        /** 每 1 个主货币单位对应积分数，默认 10（即 1 USD = 10 points） */
        private long pointsPerMajorUnit = 10L;
        /** 1 个主货币单位对应最小货币单位数量，默认 100（如 USD 的 cent） */
        private long minorPerMajorUnit = 100L;
    }

    @Data
    public static class Subscription {
        /** 订阅默认币种 */
        private String defaultCurrency = "USD";
    }
}
