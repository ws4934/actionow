package com.actionow.agent.interaction;

import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.core.agent.AgentStreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AgentStreamBridge 单元测试 — 覆盖 eventId 分配、ring 淘汰、replay 切点、
 * sink 替换语义、resync_required gap 检测、republish 保留 id 等核心行为。
 *
 * <p>这些用例同时作为 P0-2 "sink 替换场景集成/单测" 的验收：
 * 老 sink 被新 sink 替换后，后续事件只流向新 sink；旧 sink 的 unregister
 * 为 no-op，不会误删新 sink 挂载。
 */
class AgentStreamBridgeTest {

    private AgentStreamBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new AgentStreamBridge();
        // 覆盖 @Value 默认；小容量让 ring 淘汰用例更直观
        ReflectionTestUtils.setField(bridge, "maxBufferSize", 3);
        ReflectionTestUtils.setField(bridge, "maxAgeMs", 120_000L);
        ReflectionTestUtils.setField(bridge, "retentionAfterUnregisterMs", 30_000L);
    }

    @Test
    @DisplayName("publish 为每个事件分配 per-session 单调递增的 eventId")
    @SuppressWarnings("unchecked")
    void publishAssignsMonotonicEventId() {
        FluxSink<AgentStreamEvent> sink = mock(FluxSink.class);
        bridge.register("s1", sink);

        bridge.publish("s1", AgentStreamEvent.message("a"));
        bridge.publish("s1", AgentStreamEvent.message("b"));
        bridge.publish("s1", AgentStreamEvent.message("c"));

        ArgumentCaptor<AgentStreamEvent> cap = ArgumentCaptor.forClass(AgentStreamEvent.class);
        verify(sink, times(3)).next(cap.capture());
        List<AgentStreamEvent> emitted = cap.getAllValues();
        assertEquals(1L, emitted.get(0).getEventId());
        assertEquals(2L, emitted.get(1).getEventId());
        assertEquals(3L, emitted.get(2).getEventId());
        assertEquals(3L, bridge.currentMaxEventId("s1"));
    }

    @Test
    @DisplayName("replayAfter 只回放 lastEventId 之后（不含）的事件")
    @SuppressWarnings("unchecked")
    void replayAfterReturnsEventsStrictlyGreaterThanLastId() {
        FluxSink<AgentStreamEvent> prod = mock(FluxSink.class);
        bridge.register("s1", prod);
        bridge.publish("s1", AgentStreamEvent.message("a"));
        bridge.publish("s1", AgentStreamEvent.message("b"));
        bridge.publish("s1", AgentStreamEvent.message("c"));
        bridge.unregister("s1", prod);

        FluxSink<AgentStreamEvent> re = mock(FluxSink.class);
        bridge.register("s1", re);
        int n = bridge.replayAfter("s1", 1L);

        assertEquals(2, n);
        ArgumentCaptor<AgentStreamEvent> cap = ArgumentCaptor.forClass(AgentStreamEvent.class);
        verify(re, times(2)).next(cap.capture());
        assertEquals(2L, cap.getAllValues().get(0).getEventId());
        assertEquals(3L, cap.getAllValues().get(1).getEventId());
    }

    @Test
    @DisplayName("ring 淘汰后若 lastId 落在窗口外，replayAfter 发 resync_required 并返回 -1")
    @SuppressWarnings("unchecked")
    void replayAfterEmitsResyncWhenGap() {
        FluxSink<AgentStreamEvent> prod = mock(FluxSink.class);
        bridge.register("s1", prod);
        // maxBufferSize=3，publish 5 条 → buffer 保留 id {3,4,5}
        for (int i = 0; i < 5; i++) {
            bridge.publish("s1", AgentStreamEvent.message("m" + i));
        }
        bridge.unregister("s1", prod);

        FluxSink<AgentStreamEvent> re = mock(FluxSink.class);
        bridge.register("s1", re);
        // 客户端声明 lastEventId=0：oldest(3) > 0+1=1 → gap
        int n = bridge.replayAfter("s1", 0L);

        assertEquals(-1, n);
        ArgumentCaptor<AgentStreamEvent> cap = ArgumentCaptor.forClass(AgentStreamEvent.class);
        verify(re).next(cap.capture());
        AgentStreamEvent emitted = cap.getValue();
        assertEquals(AgentConstants.EVENT_RESYNC_REQUIRED, emitted.getType());
        assertEquals(0L, emitted.getMetadata().get("clientLastEventId"));
        assertEquals(3L, emitted.getMetadata().get("oldestAvailableEventId"));
        assertEquals(5L, emitted.getMetadata().get("serverMaxEventId"));
    }

    @Test
    @DisplayName("lastId 落在缓冲窗口内时，不发 resync，正常回放")
    @SuppressWarnings("unchecked")
    void replayAfterNoGapWhenLastIdCoveredByBuffer() {
        FluxSink<AgentStreamEvent> prod = mock(FluxSink.class);
        bridge.register("s1", prod);
        bridge.publish("s1", AgentStreamEvent.message("a")); // id=1
        bridge.publish("s1", AgentStreamEvent.message("b")); // id=2
        bridge.publish("s1", AgentStreamEvent.message("c")); // id=3
        bridge.unregister("s1", prod);

        FluxSink<AgentStreamEvent> re = mock(FluxSink.class);
        bridge.register("s1", re);
        // oldest=1, lastId=0 → 1 > 0+1=1 不成立 → 无 gap
        int n = bridge.replayAfter("s1", 0L);

        assertEquals(3, n);
        verify(re, times(3)).next(any());
    }

    @Test
    @DisplayName("register 替换现有 sink：后续 publish 只流向新 sink；旧 sink 的 unregister 为 no-op")
    @SuppressWarnings("unchecked")
    void registerReplacesExistingSink() {
        FluxSink<AgentStreamEvent> sink1 = mock(FluxSink.class);
        FluxSink<AgentStreamEvent> sink2 = mock(FluxSink.class);

        bridge.register("s1", sink1);
        bridge.register("s1", sink2); // 替换

        bridge.publish("s1", AgentStreamEvent.message("x"));

        verify(sink1, never()).next(any());
        verify(sink2, times(1)).next(any());

        // 旧 sink 的 unregister 不会误删新挂载
        bridge.unregister("s1", sink1);
        bridge.publish("s1", AgentStreamEvent.message("y"));
        verify(sink2, times(2)).next(any());
    }

    @Test
    @DisplayName("republish 保留源 eventId，并把本地计数器推进到不小于该 id")
    @SuppressWarnings("unchecked")
    void republishPreservesEventIdAndAdvancesCounter() {
        FluxSink<AgentStreamEvent> sink = mock(FluxSink.class);
        bridge.register("s1", sink);

        AgentStreamEvent remote = AgentStreamEvent.message("remote");
        remote.setEventId(100L);
        bridge.republish("s1", remote);

        bridge.publish("s1", AgentStreamEvent.message("local"));

        ArgumentCaptor<AgentStreamEvent> cap = ArgumentCaptor.forClass(AgentStreamEvent.class);
        verify(sink, times(2)).next(cap.capture());
        assertEquals(100L, cap.getAllValues().get(0).getEventId());
        assertEquals(101L, cap.getAllValues().get(1).getEventId());
    }

    @Test
    @DisplayName("会话无 sink 时事件仍入缓冲，重连后可回放")
    @SuppressWarnings("unchecked")
    void offlinePublishStillBuffersForLaterReplay() {
        // 无 register — 直接 publish
        bridge.publish("s1", AgentStreamEvent.message("a"));
        bridge.publish("s1", AgentStreamEvent.message("b"));

        FluxSink<AgentStreamEvent> sink = mock(FluxSink.class);
        bridge.register("s1", sink);
        int n = bridge.replayAfter("s1", 0L);

        assertEquals(2, n);
        verify(sink, times(2)).next(any());
    }

    @Test
    @DisplayName("并发 publish 分配出的 eventId 唯一且连续")
    @SuppressWarnings("unchecked")
    void concurrentPublishAssignsUniqueIds() throws Exception {
        FluxSink<AgentStreamEvent> sink = mock(FluxSink.class);
        // 大 buffer 防止淘汰干扰断言
        ReflectionTestUtils.setField(bridge, "maxBufferSize", 10_000);
        bridge.register("s2", sink);

        int threads = 8;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        bridge.publish("s2", AgentStreamEvent.message("x"));
                    }
                } catch (InterruptedException ignore) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        // 全部事件分配的 id 集合应为 [1 .. threads*perThread]，无重复/空洞
        assertEquals((long) threads * perThread, bridge.currentMaxEventId("s2"));
    }

    @Test
    @DisplayName("activeSessions 仅返回当前持有 sink 的 session")
    @SuppressWarnings("unchecked")
    void activeSessionsReflectsAttachedSinksOnly() {
        FluxSink<AgentStreamEvent> sink = mock(FluxSink.class);
        bridge.register("alive", sink);
        bridge.publish("no-sink", AgentStreamEvent.message("buffered"));

        assertTrue(bridge.activeSessions().contains("alive"));
        assertTrue(!bridge.activeSessions().contains("no-sink"));
        assertEquals(1, bridge.activeSessionCount());
    }
}
