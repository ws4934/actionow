package com.actionow.ai.plugin.auth.impl;

import com.actionow.ai.plugin.auth.AuthConfig;
import com.actionow.ai.plugin.auth.AuthenticationStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Bearer Token认证策略
 * 使用Bearer Token进行认证，支持Token刷新
 *
 * @author Actionow
 */
@Slf4j
public class BearerTokenAuthStrategy implements AuthenticationStrategy {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Token过期前提前刷新的时间（分钟）
     */
    private static final int TOKEN_REFRESH_BUFFER_MINUTES = 5;

    @Override
    public String getType() {
        return AuthConfig.AuthType.BEARER;
    }

    @Override
    public String getDisplayName() {
        return "Bearer Token认证";
    }

    @Override
    public void applyAuth(HttpHeaders headers, AuthConfig config) {
        if (!StringUtils.hasText(config.getBearerToken())) {
            throw new IllegalArgumentException("Bearer Token is required");
        }

        headers.setBearerAuth(config.getBearerToken());
    }

    @Override
    public AuthConfig refreshIfNeeded(AuthConfig config) {
        // 检查Token是否即将过期
        if (config.getTokenExpiresAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            // 如果Token将在缓冲时间内过期，需要刷新
            if (config.getTokenExpiresAt().minusMinutes(TOKEN_REFRESH_BUFFER_MINUTES).isBefore(now)) {
                // 如果有刷新Token和Token端点，执行刷新
                if (StringUtils.hasText(config.getRefreshToken()) && StringUtils.hasText(config.getTokenEndpoint())) {
                    return executeTokenRefresh(config);
                } else if (StringUtils.hasText(config.getRefreshToken())) {
                    // 有刷新Token但没有端点，记录警告
                    log.warn("[BearerTokenAuthStrategy] Token即将过期，但未配置tokenEndpoint，无法刷新");
                }
            }
        }
        return config;
    }

    /**
     * 执行Token刷新
     *
     * @param config 当前认证配置
     * @return 刷新后的认证配置
     */
    private AuthConfig executeTokenRefresh(AuthConfig config) {
        log.info("[BearerTokenAuthStrategy] 开始刷新Token, tokenEndpoint={}", config.getTokenEndpoint());

        try {
            // 构建刷新请求体（OAuth2 规范要求 URL 编码）
            StringBuilder formBody = new StringBuilder();
            formBody.append("grant_type=refresh_token");
            formBody.append("&refresh_token=").append(urlEncode(config.getRefreshToken()));

            if (StringUtils.hasText(config.getClientId())) {
                formBody.append("&client_id=").append(urlEncode(config.getClientId()));
            }
            if (StringUtils.hasText(config.getClientSecret())) {
                formBody.append("&client_secret=").append(urlEncode(config.getClientSecret()));
            }
            if (StringUtils.hasText(config.getScope())) {
                formBody.append("&scope=").append(urlEncode(config.getScope()));
            }

            // 发送刷新请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getTokenEndpoint()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[BearerTokenAuthStrategy] Token刷新失败, status={}, body={}",
                        response.statusCode(), response.body());
                throw new RuntimeException("Token刷新失败，状态码: " + response.statusCode());
            }

            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(response.body());

            String newAccessToken = jsonNode.has("access_token")
                    ? jsonNode.get("access_token").asText()
                    : null;

            if (!StringUtils.hasText(newAccessToken)) {
                throw new RuntimeException("Token刷新响应中缺少access_token");
            }

            // 构建新的配置
            AuthConfig.AuthConfigBuilder newConfigBuilder = AuthConfig.builder()
                    .authType(config.getAuthType())
                    .bearerToken(newAccessToken)
                    .tokenEndpoint(config.getTokenEndpoint())
                    .clientId(config.getClientId())
                    .clientSecret(config.getClientSecret())
                    .scope(config.getScope())
                    .customHeaders(config.getCustomHeaders())
                    .extraParams(config.getExtraParams());

            // 更新刷新Token（如果返回了新的）
            if (jsonNode.has("refresh_token")) {
                newConfigBuilder.refreshToken(jsonNode.get("refresh_token").asText());
            } else {
                newConfigBuilder.refreshToken(config.getRefreshToken());
            }

            // 计算新的过期时间
            if (jsonNode.has("expires_in")) {
                long expiresIn = jsonNode.get("expires_in").asLong();
                newConfigBuilder.tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            }

            log.info("[BearerTokenAuthStrategy] Token刷新成功");
            return newConfigBuilder.build();

        } catch (Exception e) {
            log.error("[BearerTokenAuthStrategy] Token刷新异常: {}", e.getMessage(), e);
            throw new RuntimeException("Token刷新失败: " + e.getMessage(), e);
        }
    }

    /**
     * URL 编码参数值
     * 用于 application/x-www-form-urlencoded 请求体
     *
     * @param value 原始值
     * @return URL 编码后的值
     */
    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public ValidationResult validate(AuthConfig config) {
        if (!StringUtils.hasText(config.getBearerToken())) {
            return ValidationResult.failure("Bearer Token不能为空");
        }

        // 检查Token是否已过期
        if (config.getTokenExpiresAt() != null
            && config.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            // 如果有刷新Token，提示可以刷新
            if (StringUtils.hasText(config.getRefreshToken())) {
                return ValidationResult.failure("Bearer Token已过期，请刷新Token");
            }
            return ValidationResult.failure("Bearer Token已过期");
        }

        return ValidationResult.success();
    }

    @Override
    public String[] getSensitiveFields() {
        return new String[]{"bearerToken", "refreshToken", "clientSecret"};
    }
}
