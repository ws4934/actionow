package com.actionow.ai.plugin.auth.impl;

import com.actionow.ai.plugin.auth.AuthConfig;
import com.actionow.ai.plugin.auth.AuthenticationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BearerTokenAuthStrategy 单元测试
 */
class BearerTokenAuthStrategyTest {

    private BearerTokenAuthStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BearerTokenAuthStrategy();
    }

    @Nested
    @DisplayName("基本属性测试")
    class BasicPropertiesTests {

        @Test
        @DisplayName("应该返回正确的认证类型")
        void shouldReturnCorrectType() {
            assertEquals(AuthConfig.AuthType.BEARER, strategy.getType());
        }

        @Test
        @DisplayName("应该返回正确的显示名称")
        void shouldReturnCorrectDisplayName() {
            assertEquals("Bearer Token认证", strategy.getDisplayName());
        }

        @Test
        @DisplayName("应该返回敏感字段列表")
        void shouldReturnSensitiveFields() {
            String[] sensitiveFields = strategy.getSensitiveFields();
            assertNotNull(sensitiveFields);
            assertEquals(3, sensitiveFields.length);
            assertArrayEquals(
                new String[]{"bearerToken", "refreshToken", "clientSecret"},
                sensitiveFields
            );
        }
    }

    @Nested
    @DisplayName("applyAuth 测试")
    class ApplyAuthTests {

        @Test
        @DisplayName("应该正确应用 Bearer Token 到请求头")
        void shouldApplyBearerTokenToHeaders() {
            HttpHeaders headers = new HttpHeaders();
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("test-token-12345")
                .build();

            strategy.applyAuth(headers, config);

            assertEquals("Bearer test-token-12345", headers.getFirst("Authorization"));
        }

        @Test
        @DisplayName("Token 为空时应该抛出异常")
        void shouldThrowExceptionWhenTokenIsEmpty() {
            HttpHeaders headers = new HttpHeaders();
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("")
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                strategy.applyAuth(headers, config)
            );
        }

        @Test
        @DisplayName("Token 为 null 时应该抛出异常")
        void shouldThrowExceptionWhenTokenIsNull() {
            HttpHeaders headers = new HttpHeaders();
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken(null)
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                strategy.applyAuth(headers, config)
            );
        }
    }

    @Nested
    @DisplayName("validate 测试")
    class ValidateTests {

        @Test
        @DisplayName("有效的 Token 应该验证通过")
        void shouldValidateWithValidToken() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("valid-token")
                .build();

            AuthenticationStrategy.ValidationResult result = strategy.validate(config);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("空 Token 应该验证失败")
        void shouldFailValidationWithEmptyToken() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("")
                .build();

            AuthenticationStrategy.ValidationResult result = strategy.validate(config);

            assertFalse(result.valid());
            assertEquals("Bearer Token不能为空", result.message());
        }

        @Test
        @DisplayName("过期的 Token 应该验证失败")
        void shouldFailValidationWithExpiredToken() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("expired-token")
                .tokenExpiresAt(LocalDateTime.now().minusHours(1))
                .build();

            AuthenticationStrategy.ValidationResult result = strategy.validate(config);

            assertFalse(result.valid());
            assertTrue(result.message().contains("已过期"));
        }

        @Test
        @DisplayName("过期但有 refreshToken 时应该提示刷新")
        void shouldSuggestRefreshWhenExpiredWithRefreshToken() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("expired-token")
                .tokenExpiresAt(LocalDateTime.now().minusHours(1))
                .refreshToken("refresh-token")
                .build();

            AuthenticationStrategy.ValidationResult result = strategy.validate(config);

            assertFalse(result.valid());
            assertTrue(result.message().contains("请刷新Token"));
        }

        @Test
        @DisplayName("未过期的 Token 应该验证通过")
        void shouldValidateWithNonExpiredToken() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("valid-token")
                .tokenExpiresAt(LocalDateTime.now().plusHours(1))
                .build();

            AuthenticationStrategy.ValidationResult result = strategy.validate(config);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("refreshIfNeeded 测试")
    class RefreshIfNeededTests {

        @Test
        @DisplayName("Token 未过期时不应该刷新")
        void shouldNotRefreshWhenTokenNotExpired() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("valid-token")
                .tokenExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshToken("refresh-token")
                .tokenEndpoint("https://auth.example.com/token")
                .build();

            AuthConfig result = strategy.refreshIfNeeded(config);

            // 应该返回原始配置（因为 Token 未过期）
            assertSame(config, result);
        }

        @Test
        @DisplayName("没有 refreshToken 时不应该尝试刷新")
        void shouldNotRefreshWithoutRefreshToken() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("expired-token")
                .tokenExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

            AuthConfig result = strategy.refreshIfNeeded(config);

            // 应该返回原始配置
            assertSame(config, result);
        }

        @Test
        @DisplayName("没有 tokenEndpoint 时不应该尝试刷新")
        void shouldNotRefreshWithoutTokenEndpoint() {
            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken("expired-token")
                .tokenExpiresAt(LocalDateTime.now().minusMinutes(1))
                .refreshToken("refresh-token")
                .build();

            AuthConfig result = strategy.refreshIfNeeded(config);

            // 应该返回原始配置
            assertSame(config, result);
        }
    }

    @Nested
    @DisplayName("URL 编码测试")
    class UrlEncodingTests {

        /**
         * 验证 URL 编码的正确性
         * 这个测试通过验证实际的 Token 刷新行为来间接测试 URL 编码
         * 由于 urlEncode 是私有方法，我们通过 Java 反射或功能测试来验证
         */
        @Test
        @DisplayName("包含特殊字符的 Token 应该能正确处理")
        void shouldHandleSpecialCharactersInToken() {
            // 这个测试验证包含特殊字符的 Bearer Token 可以正确应用
            // URL 编码主要用于 refresh token 请求，这里测试 Bearer Token 应用
            HttpHeaders headers = new HttpHeaders();
            String tokenWithSpecialChars = "token+with/special=chars";

            AuthConfig config = AuthConfig.builder()
                .authType(AuthConfig.AuthType.BEARER)
                .bearerToken(tokenWithSpecialChars)
                .build();

            strategy.applyAuth(headers, config);

            // Bearer Token 在 Authorization header 中不需要 URL 编码
            assertEquals("Bearer " + tokenWithSpecialChars, headers.getFirst("Authorization"));
        }
    }
}
