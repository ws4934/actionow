package com.actionow.ai.plugin.groovy.binding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmBinding 凭证安全测试
 */
class LlmBindingTest {

    @Nested
    @DisplayName("敏感信息遮掩")
    class MaskSensitiveTests {

        @Test
        @DisplayName("遮掩 OpenAI API key (sk-...)")
        void shouldMaskOpenAiKey() {
            String input = "Error with key sk-1234567890abcdefghij in request";
            String result = LlmBinding.maskSensitive(input);
            assertFalse(result.contains("1234567890abcdefghij"));
            assertTrue(result.contains("sk-***"));
        }

        @Test
        @DisplayName("遮掩 Gemini API key (AIza...)")
        void shouldMaskGeminiKey() {
            String input = "API call failed: key=AIzaSyABCDEFGHIJKLMNOP";
            String result = LlmBinding.maskSensitive(input);
            assertFalse(result.contains("AIzaSyABCDEFGHIJKLMNOP"));
            assertTrue(result.contains("AIza***"));
        }

        @Test
        @DisplayName("遮掩 Anthropic API key (sk-ant-...)")
        void shouldMaskAnthropicKey() {
            String input = "Auth failed: sk-ant-api03-abcdefghij1234567890";
            String result = LlmBinding.maskSensitive(input);
            assertFalse(result.contains("abcdefghij1234567890"));
            assertTrue(result.contains("sk-ant-***"));
        }

        @Test
        @DisplayName("遮掩 Bearer token")
        void shouldMaskBearerToken() {
            String input = "Header: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.longtoken";
            String result = LlmBinding.maskSensitive(input);
            assertFalse(result.contains("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"));
            assertTrue(result.contains("Bearer ***"));
        }

        @Test
        @DisplayName("无敏感信息时原样返回")
        void shouldReturnOriginalWhenNoSensitive() {
            String input = "Normal error message without keys";
            assertEquals(input, LlmBinding.maskSensitive(input));
        }

        @Test
        @DisplayName("null 输入返回 null")
        void shouldReturnNullForNull() {
            assertNull(LlmBinding.maskSensitive(null));
        }

        @Test
        @DisplayName("多个 key 同时遮掩")
        void shouldMaskMultipleKeys() {
            String input = "Key1: sk-abcdefghij1234567890 Key2: AIzaSyABCDEFGHIJKLMNOP";
            String result = LlmBinding.maskSensitive(input);
            assertTrue(result.contains("sk-***"));
            assertTrue(result.contains("AIza***"));
        }
    }

    @Nested
    @DisplayName("日志截断")
    class TruncateTests {

        @Test
        @DisplayName("短文本不截断")
        void shouldNotTruncateShortText() throws Exception {
            var method = LlmBinding.class.getDeclaredMethod("truncate", String.class, int.class);
            method.setAccessible(true);
            assertEquals("short", method.invoke(null, "short", 500));
        }

        @Test
        @DisplayName("长文本截断至指定长度")
        void shouldTruncateLongText() throws Exception {
            var method = LlmBinding.class.getDeclaredMethod("truncate", String.class, int.class);
            method.setAccessible(true);
            String longText = "a".repeat(1000);
            String result = (String) method.invoke(null, longText, 500);
            assertTrue(result.length() < 600); // 500 + "...(truncated)"
            assertTrue(result.endsWith("...(truncated)"));
        }
    }
}
