package com.actionow.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 *
 * @author Actionow
 */
@Data
@Component
@ConfigurationProperties(prefix = "actionow.jwt")
public class JwtProperties {

    /**
     * 密钥
     */
    private String secret = "actionow-default-secret-key-please-change-in-production";

    /**
     * 签发者
     */
    private String issuer = "actionow";

    /**
     * Access Token 过期时间（秒）默认2小时
     */
    private Long accessTokenExpire = 7200L;

    /**
     * Refresh Token 过期时间（秒）默认7天
     */
    private Long refreshTokenExpire = 604800L;

    /**
     * Token 前缀
     */
    private String tokenPrefix = "Bearer ";

    /**
     * Header 名称
     */
    private String headerName = "Authorization";
}
