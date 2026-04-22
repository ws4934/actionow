package com.actionow.ai.plugin.polling;

import com.actionow.ai.config.AiRuntimeConfigService;
import com.actionow.ai.plugin.exception.PluginRateLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PollingManager Semaphore 容量控制测试
 */
class PollingManagerTest {

    @Nested
    @DisplayName("Semaphore 容量控制")
    class CapacityTests {

        @Test
        @DisplayName("初始化时信号量 permits 等于 maxActivePolls")
        void shouldInitializeSemaphoreWithMaxPolls() throws Exception {
            AiRuntimeConfigService config = createMockConfig(50);
            PollingManager manager = new PollingManager(config);

            Semaphore semaphore = getSemaphore(manager);
            assertEquals(50, semaphore.availablePermits());

            manager.shutdown();
        }

        @Test
        @DisplayName("acquire 后可用 permits 减少")
        void semaphoreShouldDecrease() throws Exception {
            AiRuntimeConfigService config = createMockConfig(10);
            PollingManager manager = new PollingManager(config);

            Semaphore semaphore = getSemaphore(manager);
            assertTrue(semaphore.tryAcquire());
            assertEquals(9, semaphore.availablePermits());

            // release 后恢复
            semaphore.release();
            assertEquals(10, semaphore.availablePermits());

            manager.shutdown();
        }

        @Test
        @DisplayName("permits 耗尽后 tryAcquire 返回 false")
        void shouldRejectWhenExhausted() throws Exception {
            AiRuntimeConfigService config = createMockConfig(2);
            PollingManager manager = new PollingManager(config);

            Semaphore semaphore = getSemaphore(manager);
            assertTrue(semaphore.tryAcquire()); // 1
            assertTrue(semaphore.tryAcquire()); // 2
            assertFalse(semaphore.tryAcquire()); // 第 3 个应该被拒绝

            semaphore.release(2);
            manager.shutdown();
        }
    }

    // ==================== 辅助方法 ====================

    private Semaphore getSemaphore(PollingManager manager) throws Exception {
        Field field = PollingManager.class.getDeclaredField("pollSemaphore");
        field.setAccessible(true);
        return (Semaphore) field.get(manager);
    }

    /**
     * 创建一个最小化的 AiRuntimeConfigService mock
     * 只需要 getMaxActivePolls() 返回指定值
     */
    private AiRuntimeConfigService createMockConfig(int maxPolls) {
        return new AiRuntimeConfigService(null, null) {
            @Override
            public int getMaxActivePolls() {
                return maxPolls;
            }
        };
    }
}
