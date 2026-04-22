package com.actionow.billing.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubscriptionStatus 状态机单元测试
 *
 * 重点验证：
 *  - 终态（EXPIRED）拒绝任何后续转换（对应 SubscriptionBillingServiceImpl 终态保护）
 *  - CANCELED 允许回流到 ACTIVE（支持订阅复活场景）
 *  - from(String) 大小写不敏感解析
 *
 * @author Actionow
 */
class SubscriptionStatusTest {

    @Nested
    @DisplayName("canTransitionTo 状态转换矩阵")
    class CanTransitionToTests {

        @Test
        @DisplayName("TRIALING 可转换到 ACTIVE/PAST_DUE/CANCELED/EXPIRED")
        void trialingAllowedTransitions() {
            assertTrue(SubscriptionStatus.TRIALING.canTransitionTo(SubscriptionStatus.ACTIVE));
            assertTrue(SubscriptionStatus.TRIALING.canTransitionTo(SubscriptionStatus.PAST_DUE));
            assertTrue(SubscriptionStatus.TRIALING.canTransitionTo(SubscriptionStatus.CANCELED));
            assertTrue(SubscriptionStatus.TRIALING.canTransitionTo(SubscriptionStatus.EXPIRED));
        }

        @Test
        @DisplayName("ACTIVE 可转换到 PAST_DUE/CANCELED/EXPIRED 但不可回到 TRIALING")
        void activeAllowedTransitions() {
            assertTrue(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.PAST_DUE));
            assertTrue(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.CANCELED));
            assertTrue(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.EXPIRED));
            assertFalse(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.TRIALING));
        }

        @Test
        @DisplayName("PAST_DUE 可回到 ACTIVE（续费成功）")
        void pastDueCanRecoverToActive() {
            assertTrue(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.ACTIVE));
            assertTrue(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.CANCELED));
            assertTrue(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.EXPIRED));
        }

        @Test
        @DisplayName("CANCELED 只允许复活到 ACTIVE")
        void canceledOnlyReactivatesToActive() {
            assertTrue(SubscriptionStatus.CANCELED.canTransitionTo(SubscriptionStatus.ACTIVE));
            assertFalse(SubscriptionStatus.CANCELED.canTransitionTo(SubscriptionStatus.PAST_DUE));
            assertFalse(SubscriptionStatus.CANCELED.canTransitionTo(SubscriptionStatus.EXPIRED));
            assertFalse(SubscriptionStatus.CANCELED.canTransitionTo(SubscriptionStatus.TRIALING));
        }

        @Test
        @DisplayName("EXPIRED 是终态，拒绝任何转换")
        void expiredIsTerminal() {
            assertFalse(SubscriptionStatus.EXPIRED.canTransitionTo(SubscriptionStatus.ACTIVE));
            assertFalse(SubscriptionStatus.EXPIRED.canTransitionTo(SubscriptionStatus.PAST_DUE));
            assertFalse(SubscriptionStatus.EXPIRED.canTransitionTo(SubscriptionStatus.CANCELED));
            assertFalse(SubscriptionStatus.EXPIRED.canTransitionTo(SubscriptionStatus.TRIALING));
        }

        @Test
        @DisplayName("自转换恒为 true（幂等）")
        void selfTransitionAlwaysAllowed() {
            for (SubscriptionStatus status : SubscriptionStatus.values()) {
                assertTrue(status.canTransitionTo(status),
                        "Self transition should be idempotent: " + status);
            }
        }
    }

    @Nested
    @DisplayName("isTerminal 终态判定")
    class IsTerminalTests {

        @Test
        @DisplayName("只有 EXPIRED 是终态")
        void onlyExpiredIsTerminal() {
            assertTrue(SubscriptionStatus.EXPIRED.isTerminal());
            assertFalse(SubscriptionStatus.TRIALING.isTerminal());
            assertFalse(SubscriptionStatus.ACTIVE.isTerminal());
            assertFalse(SubscriptionStatus.PAST_DUE.isTerminal());
            // CANCELED 不是终态：允许订阅复活回 ACTIVE
            assertFalse(SubscriptionStatus.CANCELED.isTerminal());
        }
    }

    @Nested
    @DisplayName("from 字符串解析")
    class FromStringTests {

        @Test
        @DisplayName("支持大小写不敏感解析")
        void caseInsensitiveParsing() {
            assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.from("ACTIVE"));
            assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.from("active"));
            assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.from("Active"));
        }

        @Test
        @DisplayName("null/未知值返回 null")
        void nullAndUnknownReturnNull() {
            assertNull(SubscriptionStatus.from(null));
            assertNull(SubscriptionStatus.from("UNKNOWN_STATUS"));
            assertNull(SubscriptionStatus.from(""));
        }
    }
}
