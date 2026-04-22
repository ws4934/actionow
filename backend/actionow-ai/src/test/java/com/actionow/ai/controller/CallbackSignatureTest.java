package com.actionow.ai.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回调签名验证测试
 * 通过反射测试 CallbackController.verifySignature 的恒定时间比较
 */
class CallbackSignatureTest {

    @Nested
    @DisplayName("签名验证逻辑")
    class SignatureVerificationTests {

        @Test
        @DisplayName("正确签名应验证通过")
        void shouldVerifyCorrectSignature() throws Exception {
            String secret = "test-secret";
            Map<String, Object> body = Map.of("taskId", "123", "status", "COMPLETED");
            String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);

            // 计算期望签名
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedSig = Base64.getEncoder().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

            CallbackController controller = createController();
            boolean result = invokeVerifySignature(controller, body, expectedSig, secret);
            assertTrue(result);
        }

        @Test
        @DisplayName("错误签名应验证失败")
        void shouldRejectWrongSignature() throws Exception {
            String secret = "test-secret";
            Map<String, Object> body = Map.of("taskId", "123");

            CallbackController controller = createController();
            boolean result = invokeVerifySignature(controller, body, "wrong-signature", secret);
            assertFalse(result);
        }

        @Test
        @DisplayName("空签名空密钥应跳过验证返回 true")
        void shouldSkipWhenNoSignatureConfigured() throws Exception {
            Map<String, Object> body = Map.of("taskId", "123");

            CallbackController controller = createController();
            // null signature
            assertTrue(invokeVerifySignature(controller, body, null, "secret"));
            // null secret
            assertTrue(invokeVerifySignature(controller, body, "sig", null));
            // both empty
            assertTrue(invokeVerifySignature(controller, body, "", ""));
        }

        @Test
        @DisplayName("恒定时间比较: MessageDigest.isEqual 行为一致")
        void constantTimeComparisonBehavior() {
            // 验证 MessageDigest.isEqual 的基本行为
            byte[] a = "test123".getBytes(StandardCharsets.UTF_8);
            byte[] b = "test123".getBytes(StandardCharsets.UTF_8);
            byte[] c = "test456".getBytes(StandardCharsets.UTF_8);

            assertTrue(MessageDigest.isEqual(a, b));
            assertFalse(MessageDigest.isEqual(a, c));
        }
    }

    // ==================== 辅助方法 ====================

    private CallbackController createController() throws Exception {
        var constructor = CallbackController.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        // 传 null 依赖，只测 verifySignature
        Object[] args = new Object[constructor.getParameterCount()];
        return (CallbackController) constructor.newInstance(args);
    }

    private boolean invokeVerifySignature(CallbackController controller,
                                           Map<String, Object> body,
                                           String signature, String secret) throws Exception {
        Method method = CallbackController.class.getDeclaredMethod(
                "verifySignature", Map.class, String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, body, signature, secret);
    }
}
