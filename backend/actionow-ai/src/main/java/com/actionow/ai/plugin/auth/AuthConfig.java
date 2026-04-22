package com.actionow.ai.plugin.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证配置
 * 统一存储不同认证方式的配置信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthConfig {

    /**
     * 认证类型
     * API_KEY, AK_SK, BEARER, OAUTH2, CUSTOM
     */
    private String authType;

    // ==================== API Key 配置 ====================

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * API密钥头名称（默认Authorization）
     */
    @Builder.Default
    private String apiKeyHeader = "Authorization";

    /**
     * API密钥前缀（如 "Bearer ", "Api-Key "）
     */
    @Builder.Default
    private String apiKeyPrefix = "Bearer ";

    /**
     * API密钥位置（HEADER, QUERY, BODY）
     */
    @Builder.Default
    private String apiKeyLocation = "HEADER";

    /**
     * 查询参数名（当位置为QUERY时使用）
     */
    private String apiKeyQueryParam;

    // ==================== AK/SK 配置 ====================

    /**
     * Access Key
     */
    private String accessKey;

    /**
     * Secret Key
     */
    private String secretKey;

    /**
     * 签名算法（如 HmacSHA256）
     */
    @Builder.Default
    private String signatureAlgorithm = "HmacSHA256";

    /**
     * 签名头名称
     */
    @Builder.Default
    private String signatureHeader = "X-Signature";

    /**
     * 时间戳头名称
     */
    @Builder.Default
    private String timestampHeader = "X-Timestamp";

    /**
     * 签名版本
     */
    @Builder.Default
    private String signatureVersion = "1";

    /**
     * 服务名称（用于AWS风格签名）
     */
    private String serviceName;

    /**
     * 区域（用于AWS风格签名）
     */
    private String region;

    // ==================== Bearer Token 配置 ====================

    /**
     * Bearer Token
     */
    private String bearerToken;

    /**
     * Token过期时间
     */
    private LocalDateTime tokenExpiresAt;

    // ==================== OAuth2 配置 ====================

    /**
     * OAuth2 客户端ID
     */
    private String clientId;

    /**
     * OAuth2 客户端密钥
     */
    private String clientSecret;

    /**
     * OAuth2 Token端点
     */
    private String tokenEndpoint;

    /**
     * OAuth2 刷新Token
     */
    private String refreshToken;

    /**
     * OAuth2 授权范围
     */
    private String scope;

    /**
     * OAuth2 授权类型
     */
    @Builder.Default
    private String grantType = "client_credentials";

    // ==================== 自定义头配置 ====================

    /**
     * 自定义请求头
     */
    private Map<String, String> customHeaders;

    // ==================== 通用配置 ====================

    /**
     * 额外参数（扩展用）
     */
    private Map<String, Object> extraParams;

    /**
     * 创建API Key配置
     */
    public static AuthConfig apiKey(String apiKey) {
        return AuthConfig.builder()
                .authType(AuthType.API_KEY)
                .apiKey(apiKey)
                .build();
    }

    /**
     * 创建API Key配置（自定义头）
     */
    public static AuthConfig apiKey(String apiKey, String headerName, String prefix) {
        return AuthConfig.builder()
                .authType(AuthType.API_KEY)
                .apiKey(apiKey)
                .apiKeyHeader(headerName)
                .apiKeyPrefix(prefix)
                .build();
    }

    /**
     * 创建AK/SK配置
     */
    public static AuthConfig akSk(String accessKey, String secretKey) {
        return AuthConfig.builder()
                .authType(AuthType.AK_SK)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .build();
    }

    /**
     * 创建Bearer Token配置
     */
    public static AuthConfig bearer(String token) {
        return AuthConfig.builder()
                .authType(AuthType.BEARER)
                .bearerToken(token)
                .build();
    }

    /**
     * 创建自定义头配置
     */
    public static AuthConfig custom(Map<String, String> headers) {
        return AuthConfig.builder()
                .authType(AuthType.CUSTOM)
                .customHeaders(headers)
                .build();
    }

    /**
     * 认证类型常量
     */
    public static final class AuthType {
        public static final String API_KEY = "API_KEY";
        public static final String AK_SK = "AK_SK";
        public static final String BEARER = "BEARER";
        public static final String OAUTH2 = "OAUTH2";
        public static final String CUSTOM = "CUSTOM";
        public static final String NONE = "NONE";

        private AuthType() {}
    }
}
