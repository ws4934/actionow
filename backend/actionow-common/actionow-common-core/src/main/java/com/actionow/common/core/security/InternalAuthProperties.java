package com.actionow.common.core.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内部服务认证配置
 * 用于网关签名和服务间调用的HMAC-SHA256认证
 *
 * @author Actionow
 */
@Data
@Component
@ConfigurationProperties(prefix = "actionow.internal")
public class InternalAuthProperties {

    /**
     * HMAC签名密钥
     */
    private String authSecret;

    /**
     * 签名最大有效期（秒），默认5分钟
     */
    private long signatureMaxAgeSeconds = 300;

    /**
     * 内部短JWT有效期（秒），默认60秒
     */
    private long internalTokenExpireSeconds = 60;

    /**
     * 判断是否已配置密钥
     */
    public boolean isConfigured() {
        return authSecret != null && !authSecret.isBlank();
    }
}
