package com.actionow.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SafeUrlValidator 单元测试
 * 验证 SSRF 防护逻辑
 */
class SafeUrlValidatorTest {

    @Nested
    @DisplayName("合法 URL")
    class ValidUrls {

        @Test
        @DisplayName("HTTPS 公网 IP 应通过")
        void shouldAcceptHttps() {
            assertDoesNotThrow(() -> SafeUrlValidator.validate("https://8.8.8.8/file.png"));
        }

        @Test
        @DisplayName("HTTP 公网 IP 应通过")
        void shouldAcceptHttp() {
            assertDoesNotThrow(() -> SafeUrlValidator.validate("http://1.1.1.1/image.jpg"));
        }

        @Test
        @DisplayName("带端口的公网 URL 应通过")
        void shouldAcceptWithPort() {
            assertDoesNotThrow(() -> SafeUrlValidator.validate("https://8.8.4.4:8443/api/file"));
        }
    }

    @Nested
    @DisplayName("非法 scheme")
    class InvalidSchemes {

        @Test
        @DisplayName("file:// 应拒绝")
        void shouldRejectFileScheme() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("file:///etc/passwd"));
            assertTrue(ex.getMessage().contains("scheme"));
        }

        @Test
        @DisplayName("ftp:// 应拒绝")
        void shouldRejectFtpScheme() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("ftp://server/file"));
        }

        @Test
        @DisplayName("无 scheme 应拒绝")
        void shouldRejectNoScheme() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("just-a-string"));
        }
    }

    @Nested
    @DisplayName("内网 IP 拒绝")
    class PrivateIpRejection {

        @Test
        @DisplayName("10.x.x.x 应拒绝")
        void shouldReject10Network() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("http://10.0.0.1/secret"));
        }

        @Test
        @DisplayName("192.168.x.x 应拒绝")
        void shouldReject192168() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("http://192.168.1.1/admin"));
        }

        @Test
        @DisplayName("172.16.x.x 应拒绝")
        void shouldReject172Private() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("http://172.16.0.1/internal"));
        }
    }

    @Nested
    @DisplayName("环回地址拒绝")
    class LoopbackRejection {

        @Test
        @DisplayName("127.0.0.1 应拒绝")
        void shouldRejectLoopback() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("http://127.0.0.1/"));
        }

        @Test
        @DisplayName("localhost 应拒绝")
        void shouldRejectLocalhost() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("http://localhost/admin"));
        }

        @Test
        @DisplayName("IPv6 loopback [::1] 应拒绝")
        void shouldRejectIpv6Loopback() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("http://[::1]/"));
        }
    }

    @Nested
    @DisplayName("云元数据端点拒绝")
    class MetadataRejection {

        @Test
        @DisplayName("169.254.169.254 应拒绝")
        void shouldRejectMetadataEndpoint() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("http://169.254.169.254/latest/meta-data/"));
        }
    }

    @Nested
    @DisplayName("空值和无效输入")
    class NullAndInvalid {

        @Test
        @DisplayName("null 应拒绝")
        void shouldRejectNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate(null));
        }

        @Test
        @DisplayName("空字符串应拒绝")
        void shouldRejectEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate(""));
        }

        @Test
        @DisplayName("空白字符串应拒绝")
        void shouldRejectBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> SafeUrlValidator.validate("   "));
        }
    }
}
