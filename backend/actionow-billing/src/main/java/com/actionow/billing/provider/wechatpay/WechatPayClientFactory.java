package com.actionow.billing.provider.wechatpay;

import com.actionow.billing.config.BillingProperties;
import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.common.core.exception.BusinessException;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 微信支付 SDK 客户端工厂
 * 懒加载 + 单例，自动管理平台证书下载与更新
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatPayClientFactory {

    private final BillingProperties billingProperties;

    private volatile RSAAutoCertificateConfig config;
    private volatile NativePayService nativePayService;
    private volatile NotificationParser notificationParser;

    public NativePayService getNativePayService() {
        if (nativePayService == null) {
            synchronized (this) {
                if (nativePayService == null) {
                    nativePayService = new NativePayService.Builder().config(getConfig()).build();
                }
            }
        }
        return nativePayService;
    }

    public NotificationParser getNotificationParser() {
        if (notificationParser == null) {
            synchronized (this) {
                if (notificationParser == null) {
                    notificationParser = new NotificationParser((NotificationConfig) getConfig());
                }
            }
        }
        return notificationParser;
    }

    private Config getConfig() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    BillingProperties.WechatPay wechatPay = billingProperties.getWechatpay();
                    validateConfig(wechatPay);

                    String privateKeyContent = loadPrivateKey(wechatPay.getPrivateKeyPath());
                    config = new RSAAutoCertificateConfig.Builder()
                            .merchantId(wechatPay.getMchId())
                            .privateKey(privateKeyContent)
                            .merchantSerialNumber(wechatPay.getMchSerialNumber())
                            .apiV3Key(wechatPay.getApiV3Key())
                            .build();
                    log.info("微信支付 SDK 初始化成功: mchId={}", wechatPay.getMchId());
                }
            }
        }
        return config;
    }

    /**
     * 加载私钥内容，支持 classpath: 和文件系统路径
     */
    private String loadPrivateKey(String path) {
        try {
            Resource resource = new DefaultResourceLoader().getResource(path);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new BusinessException(BillingErrorCode.WECHATPAY_CONFIG_MISSING,
                    "无法读取商户私钥: " + path + " - " + e.getMessage());
        }
    }

    private void validateConfig(BillingProperties.WechatPay wechatPay) {
        if (wechatPay.getMchId() == null || wechatPay.getMchId().isBlank()) {
            throw new BusinessException(BillingErrorCode.WECHATPAY_CONFIG_MISSING, "商户号未配置");
        }
        if (wechatPay.getApiV3Key() == null || wechatPay.getApiV3Key().isBlank()) {
            throw new BusinessException(BillingErrorCode.WECHATPAY_CONFIG_MISSING, "APIv3 密钥未配置");
        }
        if (wechatPay.getPrivateKeyPath() == null || wechatPay.getPrivateKeyPath().isBlank()) {
            throw new BusinessException(BillingErrorCode.WECHATPAY_CONFIG_MISSING, "商户私钥路径未配置");
        }
        if (wechatPay.getMchSerialNumber() == null || wechatPay.getMchSerialNumber().isBlank()) {
            throw new BusinessException(BillingErrorCode.WECHATPAY_CONFIG_MISSING, "商户证书序列号未配置");
        }
    }
}
