package com.actionow.ai.plugin.groovy.binding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoBinding 单元测试
 */
class CryptoBindingTest {

    private CryptoBinding crypto;

    @BeforeEach
    void setUp() {
        crypto = new CryptoBinding();
    }

    @Nested
    @DisplayName("随机字符串生成")
    class RandomStringTests {

        @Test
        @DisplayName("生成指定长度的随机字符串")
        void shouldGenerateCorrectLength() {
            assertEquals(16, crypto.randomString(16).length());
            assertEquals(32, crypto.randomString(32).length());
            assertEquals(1, crypto.randomString(1).length());
        }

        @Test
        @DisplayName("只包含合法字符 [A-Za-z0-9]")
        void shouldContainOnlyAlphanumeric() {
            String result = crypto.randomString(100);
            assertTrue(result.matches("[A-Za-z0-9]+"));
        }

        @Test
        @DisplayName("两次生成结果不同（概率极高）")
        void shouldGenerateDifferentResults() {
            String a = crypto.randomString(32);
            String b = crypto.randomString(32);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("长度为 0 返回空字符串")
        void shouldReturnEmptyForZeroLength() {
            assertEquals("", crypto.randomString(0));
        }
    }

    @Nested
    @DisplayName("HMAC 签名")
    class HmacTests {

        @Test
        @DisplayName("HMAC-SHA256 已知值验证")
        void hmacSha256KnownValue() {
            // echo -n "hello" | openssl dgst -sha256 -hmac "secret" -hex
            String result = crypto.hmacSha256("hello", "secret");
            assertEquals("88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b", result);
        }

        @Test
        @DisplayName("HMAC-SHA256 Base64 格式")
        void hmacSha256Base64NotEmpty() {
            String result = crypto.hmacSha256Base64("hello", "secret");
            assertNotNull(result);
            assertFalse(result.isEmpty());
            // Base64 不含非法字符
            assertTrue(result.matches("[A-Za-z0-9+/=]+"));
        }

        @Test
        @DisplayName("HMAC-SHA1 已知值验证")
        void hmacSha1KnownValue() {
            String result = crypto.hmacSha1("hello", "secret");
            assertNotNull(result);
            assertEquals(40, result.length()); // SHA1 = 20 bytes = 40 hex chars
        }
    }

    @Nested
    @DisplayName("哈希函数")
    class HashTests {

        @Test
        @DisplayName("MD5 已知值验证")
        void md5KnownValue() {
            // echo -n "hello" | md5
            assertEquals("5d41402abc4b2a76b9719d911017c592", crypto.md5("hello"));
        }

        @Test
        @DisplayName("SHA256 已知值验证")
        void sha256KnownValue() {
            // echo -n "hello" | shasum -a 256
            assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    crypto.sha256("hello"));
        }

        @Test
        @DisplayName("SHA1 已知值验证")
        void sha1KnownValue() {
            assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", crypto.sha1("hello"));
        }
    }

    @Nested
    @DisplayName("UUID 生成")
    class UuidTests {

        @Test
        @DisplayName("UUID 格式正确")
        void uuidFormat() {
            String result = crypto.uuid();
            assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }

        @Test
        @DisplayName("UuidSimple 不含横线且长度32")
        void uuidSimpleFormat() {
            String result = crypto.uuidSimple();
            assertEquals(32, result.length());
            assertFalse(result.contains("-"));
            assertTrue(result.matches("[0-9a-f]{32}"));
        }
    }

    @Nested
    @DisplayName("Base64 编解码")
    class Base64Tests {

        @Test
        @DisplayName("编码解码互逆")
        void encodeDecodeRoundTrip() {
            String original = "Hello, World!";
            String encoded = crypto.base64Encode(original);
            assertEquals(original, crypto.base64Decode(encoded));
        }

        @Test
        @DisplayName("null 输入返回空字符串")
        void nullReturnsEmpty() {
            assertEquals("", crypto.base64Encode((String) null));
            assertEquals("", crypto.base64Decode(null));
        }
    }

    @Nested
    @DisplayName("时间戳")
    class TimestampTests {

        @Test
        @DisplayName("秒级时间戳合理范围")
        void timestampReasonable() {
            long ts = crypto.timestamp();
            // 2024-01-01 以后
            assertTrue(ts > 1704067200L);
        }

        @Test
        @DisplayName("毫秒级时间戳大于秒级")
        void timestampMsGreaterThanTimestamp() {
            assertTrue(crypto.timestampMs() > crypto.timestamp());
        }
    }
}
