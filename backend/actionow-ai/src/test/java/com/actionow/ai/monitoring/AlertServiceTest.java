package com.actionow.ai.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlertService 冷却机制测试
 * 通过反射直接测试 cooldown map 逻辑，不启动 Spring 上下文
 */
class AlertServiceTest {

    @Nested
    @DisplayName("冷却机制测试")
    class CooldownTests {

        @Test
        @DisplayName("新 key 不在冷却期")
        void newKeyShouldNotBeInCooldown() throws Exception {
            AlertService service = createServiceWithNullDeps();
            assertFalse(invokeIsInCooldown(service, "test:provider1"));
        }

        @Test
        @DisplayName("刚设置的 key 在冷却期内")
        void recentKeyShouldBeInCooldown() throws Exception {
            AlertService service = createServiceWithNullDeps();
            Map<String, LocalDateTime> cooldowns = getCooldownMap(service);
            cooldowns.put("test:provider1", LocalDateTime.now());

            assertTrue(invokeIsInCooldown(service, "test:provider1"));
        }

        @Test
        @DisplayName("过期的 key 不在冷却期")
        void expiredKeyShouldNotBeInCooldown() throws Exception {
            AlertService service = createServiceWithNullDeps();
            Map<String, LocalDateTime> cooldowns = getCooldownMap(service);
            // 设置 30 分钟前（远超 10 分钟冷却期）
            cooldowns.put("test:provider1", LocalDateTime.now().minusMinutes(30));

            assertFalse(invokeIsInCooldown(service, "test:provider1"));
        }

        @Test
        @DisplayName("cleanupCooldowns 清除过期条目")
        void cleanupShouldRemoveExpiredEntries() throws Exception {
            AlertService service = createServiceWithNullDeps();
            Map<String, LocalDateTime> cooldowns = getCooldownMap(service);

            // 一个过期条目
            cooldowns.put("expired:provider1", LocalDateTime.now().minusMinutes(30));
            // 一个未过期条目
            cooldowns.put("active:provider2", LocalDateTime.now());

            service.cleanupCooldowns();

            assertFalse(cooldowns.containsKey("expired:provider1"));
            assertTrue(cooldowns.containsKey("active:provider2"));
        }

        @Test
        @DisplayName("cleanupCooldowns 空 map 不报错")
        void cleanupEmptyMapShouldNotFail() throws Exception {
            AlertService service = createServiceWithNullDeps();
            assertDoesNotThrow(service::cleanupCooldowns);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射构造 AlertService（依赖置 null，仅测试冷却逻辑）
     */
    private AlertService createServiceWithNullDeps() throws Exception {
        var constructor = AlertService.class.getDeclaredConstructor(
                com.actionow.common.mq.producer.MessageProducer.class,
                MetricsCollector.class,
                com.actionow.ai.config.AiRuntimeConfigService.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, LocalDateTime> getCooldownMap(AlertService service) throws Exception {
        Field field = AlertService.class.getDeclaredField("alertCooldowns");
        field.setAccessible(true);
        return (Map<String, LocalDateTime>) field.get(service);
    }

    private boolean invokeIsInCooldown(AlertService service, String key) throws Exception {
        Method method = AlertService.class.getDeclaredMethod("isInCooldown", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, key);
    }
}
