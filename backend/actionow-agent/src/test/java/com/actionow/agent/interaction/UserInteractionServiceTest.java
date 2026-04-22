package com.actionow.agent.interaction;

import com.actionow.agent.metrics.AgentMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UserInteractionService HITL E2E 测试
 *
 * <p>覆盖 P5.1 跨 Pod Pub/Sub + P0.2 兜底轮询 的核心路径：
 * <ol>
 *   <li>本地 fast path：submitAnswer 唤醒 awaitAnswer</li>
 *   <li>超时：awaitAnswer 抛 TimeoutException 并回收资源</li>
 *   <li>取消：cancelAsk 让等待线程以异常结束</li>
 *   <li>重复 askId：awaitAnswer 抛 IllegalStateException</li>
 *   <li>跨 Pod 唤醒：onMessage 从 Redis 读 answer 完成 future</li>
 *   <li>Pub/Sub 丢消息：兜底轮询 pollPendingAsks 仍能唤醒</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class UserInteractionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private RedisMessageListenerContainer listenerContainer;

    private ObjectMapper objectMapper;
    private AgentMetrics metrics;
    private AgentAskHistoryService askHistoryService;
    private UserInteractionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        metrics = new AgentMetrics(new SimpleMeterRegistry());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().doNothing().when(listenerContainer).addMessageListener(any(), any(ChannelTopic.class));
        askHistoryService = mock(AgentAskHistoryService.class);
        service = new UserInteractionService(redisTemplate, objectMapper, listenerContainer, metrics, askHistoryService);
        service.subscribeNotifications();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    @DisplayName("本地 fast path：submitAnswer 在当前 Pod 唤醒 awaitAnswer")
    void localFastPath_submitAnswerWakesAwaitAnswer() throws Exception {
        String askId = service.newAskId();
        CompletableFuture<UserAnswer> asyncAwait = CompletableFuture.supplyAsync(() -> {
            try {
                return service.awaitAnswer("s1", askId, Duration.ofSeconds(5));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 给等待线程时间注册 pending ask
        Thread.sleep(100);
        assertEquals(1, service.pendingCount());

        service.submitAnswer("s1", askId, UserAnswer.builder().answer("yes").build());
        UserAnswer result = asyncAwait.get(2, TimeUnit.SECONDS);
        assertEquals("yes", result.getAnswer());
        assertEquals(0, service.pendingCount());
    }

    @Test
    @DisplayName("超时：awaitAnswer 在 timeout 后抛 TimeoutException 并清理 pending")
    void timeout_awaitAnswerThrowsAndCleansPending() {
        String askId = service.newAskId();
        assertThrows(TimeoutException.class,
                () -> service.awaitAnswer("s1", askId, Duration.ofMillis(200)));
        assertEquals(0, service.pendingCount());
    }

    @Test
    @DisplayName("取消：cancelAsk 让等待线程以异常结束")
    void cancel_cancelAskThrowsInWaiter() throws Exception {
        String askId = service.newAskId();
        CompletableFuture<Throwable> asyncAwait = CompletableFuture.supplyAsync(() -> {
            try {
                service.awaitAnswer("s1", askId, Duration.ofSeconds(5));
                return null;
            } catch (Throwable t) {
                return t;
            }
        });

        Thread.sleep(100);
        service.cancelAsk("s1", askId, "user closed");

        Throwable t = asyncAwait.get(2, TimeUnit.SECONDS);
        assertNotNull(t, "expected await to end with exception");
        assertTrue(t.getMessage().contains("取消") || t.getCause() != null);
        assertEquals(0, service.pendingCount());
    }

    @Test
    @DisplayName("重复 askId：awaitAnswer 抛 IllegalStateException")
    void duplicateAskId_rejected() throws Exception {
        String askId = service.newAskId();
        CompletableFuture.runAsync(() -> {
            try {
                service.awaitAnswer("s1", askId, Duration.ofSeconds(2));
            } catch (Exception ignored) {}
        });
        Thread.sleep(50);

        assertThrows(IllegalStateException.class,
                () -> service.awaitAnswer("s1", askId, Duration.ofSeconds(1)));

        service.cancelAsk("s1", askId, "cleanup");
    }

    @Test
    @DisplayName("跨 Pod 唤醒：onMessage 从 Redis 拉 answer 完成 future")
    void crossPod_onMessageCompletesFuture() throws Exception {
        String askId = service.newAskId();
        String answerJson = objectMapper.writeValueAsString(
                UserAnswer.builder().answer("option-1").build());
        when(valueOps.get(eq("agent:ask:answer:" + askId))).thenReturn(answerJson);

        CompletableFuture<UserAnswer> asyncAwait = CompletableFuture.supplyAsync(() -> {
            try {
                return service.awaitAnswer("s2", askId, Duration.ofSeconds(5));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(100);
        // 模拟 Pub/Sub 从其它 Pod 广播过来
        service.onMessage(new DefaultMessage(
                "agent:ask:notify".getBytes(StandardCharsets.UTF_8),
                askId.getBytes(StandardCharsets.UTF_8)), null);

        UserAnswer result = asyncAwait.get(2, TimeUnit.SECONDS);
        assertEquals("option-1", result.getAnswer());
    }

    @Test
    @DisplayName("Pub/Sub 丢消息：兜底轮询从 Redis 读取 answer 唤醒等待线程")
    void fallbackPoll_recoversWhenPubSubMissed() throws Exception {
        // 用一个更短轮询周期的实例来避免 15s 真等待。
        // 直接构造一个私有 service 并不走 subscribeNotifications 的 15s 调度——
        // 改为显式调用反射访问内部 pollPendingAsks 逻辑。
        String askId = service.newAskId();
        String answerJson = objectMapper.writeValueAsString(
                UserAnswer.builder().answer("fallback").build());
        when(valueOps.get(eq("agent:ask:answer:" + askId))).thenReturn(answerJson);

        CompletableFuture<UserAnswer> asyncAwait = CompletableFuture.supplyAsync(() -> {
            try {
                return service.awaitAnswer("s3", askId, Duration.ofSeconds(5));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(100);
        // 不走 onMessage（模拟 Pub/Sub 丢消息），直接触发一次兜底扫描
        invokePrivatePoll(service);

        UserAnswer result = asyncAwait.get(2, TimeUnit.SECONDS);
        assertEquals("fallback", result.getAnswer());
    }

    @Test
    @DisplayName("级联取消：cancelAllBySessionId 同时结束同一 session 下的多个 pending ask")
    void cascadeCancel_sessionCancelCancelsAllAsks() throws Exception {
        String askA = service.newAskId();
        String askB = service.newAskId();

        CompletableFuture<Throwable> waitA = CompletableFuture.supplyAsync(() -> {
            try { service.awaitAnswer("sx", askA, Duration.ofSeconds(5)); return null; }
            catch (Throwable t) { return t; }
        });
        CompletableFuture<Throwable> waitB = CompletableFuture.supplyAsync(() -> {
            try { service.awaitAnswer("sx", askB, Duration.ofSeconds(5)); return null; }
            catch (Throwable t) { return t; }
        });

        Thread.sleep(100);
        assertEquals(2, service.pendingCount());

        int cancelled = service.cancelAllBySessionId("sx", "session cancelled");
        assertEquals(2, cancelled);

        assertNotNull(waitA.get(2, TimeUnit.SECONDS));
        assertNotNull(waitB.get(2, TimeUnit.SECONDS));
        assertEquals(0, service.pendingCount());
    }

    private void invokePrivatePoll(UserInteractionService svc) throws Exception {
        java.lang.reflect.Method m = UserInteractionService.class.getDeclaredMethod("pollPendingAsks");
        m.setAccessible(true);
        m.invoke(svc);
    }
}
