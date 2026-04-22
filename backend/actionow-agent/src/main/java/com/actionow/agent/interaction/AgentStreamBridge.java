package com.actionow.agent.interaction;

import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.core.agent.AgentStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 活跃 SSE 会话的 sink 注册表 + per-session 事件 ID 分配 + 环形回放缓冲。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>{@code register / unregister} — SaaAgentRunner 在流开始 / 结束时挂接 sink。</li>
 *   <li>{@code publish} — 分配 eventId、存入缓冲、下发给当前 sink（若存在）。
 *       缓冲用于客户端断线重连后的回放；无 sink 时事件仍会被缓冲，等待重连。</li>
 *   <li>{@code replayAfter} — 客户端重连并携带 Last-Event-ID 时，把缓冲里 id &gt; lastId 的事件
 *       依序 flush 给新 sink；随后实时事件照常经由 publish 下发，从而做到"零丢事件恢复"。</li>
 *   <li>{@code activeSessions} — 供心跳调度器拉取需要发心跳的活跃会话列表。</li>
 * </ul>
 *
 * <h2>缓冲容量</h2>
 * 每会话默认保留最近 200 条事件或 {@code maxAgeMs} 毫秒内的事件（取二者并集），在写入时
 * 按容量淘汰最旧项。回放场景的上限为"典型一次生成的总事件量"，超出则视为超时，前端应当
 * 走 {@code /agent/sessions/{id}/state} 端点重新对齐而非继续回放。
 *
 * <h2>并发与内存</h2>
 * buffer / counters 均为 per-session；session 生命周期绑定在 {@link #unregister} 后不会立即
 * 销毁 —— 保留 {@code retentionAfterUnregisterMs} 毫秒以容忍"短时掉线-重连"的边缘情况。
 * 更长久的掉线应由 state 端点 + 持久化消息恢复。
 *
 * <p>本类仍为"同 pod 内存"实现；跨 pod 场景由 P3 阶段 Redis Stream 事件总线接管，
 * 但 API 契约（register / publish / replayAfter / activeSessions）保持不变，升级可平滑替换。
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AgentStreamBridge {

    private final Map<String, SessionChannel> channels = new ConcurrentHashMap<>();

    @Value("${actionow.agent.stream.buffer.max-size:200}")
    private int maxBufferSize;

    @Value("${actionow.agent.stream.buffer.max-age-ms:120000}")
    private long maxAgeMs;

    @Value("${actionow.agent.stream.buffer.retain-after-unregister-ms:30000}")
    private long retentionAfterUnregisterMs;

    /**
     * 可选的跨 pod fan-out；未注入时退化为纯 pod 内存模式。
     * 使用 Setter + ObjectProvider 注入，避免循环依赖（fanout 需要引用 bridge）。
     */
    private org.springframework.beans.factory.ObjectProvider<AgentStreamRedisFanout> fanoutProvider;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFanoutProvider(
            org.springframework.beans.factory.ObjectProvider<AgentStreamRedisFanout> fanoutProvider) {
        this.fanoutProvider = fanoutProvider;
    }

    /** 流开始时注册当前 sink；若已存在 channel（重连场景）则复用，保留既有缓冲。 */
    public void register(String sessionId, FluxSink<AgentStreamEvent> sink) {
        if (sessionId == null || sink == null) return;
        SessionChannel ch = channels.computeIfAbsent(sessionId, k -> new SessionChannel());
        ch.attachSink(sink);
        log.debug("AgentStreamBridge register: sessionId={}, activeChannels={}", sessionId, channels.size());
    }

    /** 流结束/异常/取消时注销；仅当当前 sink 与传入 sink 匹配才移除（防止后续 stream 被误注销）。 */
    public void unregister(String sessionId, FluxSink<AgentStreamEvent> sink) {
        if (sessionId == null) return;
        SessionChannel ch = channels.get(sessionId);
        if (ch != null) {
            ch.detachSink(sink);
        }
        log.debug("AgentStreamBridge unregister: sessionId={}, activeChannels={}", sessionId, channels.size());
    }

    /**
     * 发布事件：分配 eventId → 入缓冲 → 推给当前 sink（若有）。
     * 若会话当前无 sink（客户端掉线中），事件仍然会被缓冲，等待重连后 {@link #replayAfter} 补发。
     */
    public void publish(String sessionId, AgentStreamEvent event) {
        if (sessionId == null || event == null) return;
        SessionChannel ch = channels.computeIfAbsent(sessionId, k -> new SessionChannel());
        long id = ch.nextEventId();
        event.setEventId(id);
        ch.bufferEvent(event);
        FluxSink<AgentStreamEvent> sink = ch.currentSink();
        if (sink != null) {
            try {
                sink.next(event);
            } catch (Exception e) {
                log.warn("AgentStreamBridge publish failed sessionId={}, type={}, eventId={}: {}",
                        sessionId, event.getType(), id, e.getMessage());
            }
        } else if (!AgentConstants.EVENT_HEARTBEAT.equals(event.getType())) {
            log.debug("AgentStreamBridge buffering (no active sink): sessionId={}, type={}, eventId={}",
                    sessionId, event.getType(), id);
        }
        // 跨 pod fan-out（仅当 fanout bean 启用时）。放在本地分发之后，
        // 让本 pod 的延迟不受 Redis 网络抖动影响。
        if (fanoutProvider != null) {
            AgentStreamRedisFanout fanout = fanoutProvider.getIfAvailable();
            if (fanout != null) fanout.broadcast(sessionId, event);
        }
    }

    /**
     * 跨 pod 事件注入 — 保留源 eventId 不重新分配，仅写入缓冲 + 下发给当前 sink。
     *
     * <p>用途：{@code AgentStreamRedisFanout} 在 Redis Pub/Sub 订阅回调中调用，避免
     * 每个 pod 给同一事件分配不同的本地 id，从而破坏客户端"lastEventId 单调"假设。
     *
     * <p>本 pod 产生的事件只应走 {@link #publish}；跨 pod 来的事件走此方法。
     */
    public void republish(String sessionId, AgentStreamEvent event) {
        if (sessionId == null || event == null || event.getEventId() == null) return;
        SessionChannel ch = channels.computeIfAbsent(sessionId, k -> new SessionChannel());
        ch.adoptRemoteEventId(event.getEventId());
        ch.bufferEvent(event);
        FluxSink<AgentStreamEvent> sink = ch.currentSink();
        if (sink != null) {
            try {
                sink.next(event);
            } catch (Exception e) {
                log.warn("AgentStreamBridge republish failed sessionId={}, type={}, eventId={}: {}",
                        sessionId, event.getType(), event.getEventId(), e.getMessage());
            }
        }
    }

    /**
     * 客户端重连时回放 {@code lastEventId} 之后（不含）的缓冲事件。
     * 必须在 {@link #register} 之后、实时事件继续下发之前调用。
     *
     * <h3>间隙检测</h3>
     * 若 {@code lastEventId} 已落在环形缓冲之外（缓冲最老 id &gt; lastEventId + 1，
     * 或缓冲为空但服务端已分配过更大的 id），会先下发一条 {@code resync_required} 事件，
     * 前端收到后应停止依赖增量回放，改走 {@code /agent/sessions/{id}/state +
     * /messages} 全量对齐。resync 事件本身不入缓冲，避免循环。
     *
     * @return 实际回放的事件数；负数表示触发了 resync（前端应忽略增量并全量对齐）
     */
    public int replayAfter(String sessionId, long lastEventId) {
        SessionChannel ch = channels.get(sessionId);
        if (ch == null) return 0;
        FluxSink<AgentStreamEvent> sink = ch.currentSink();
        if (sink == null) return 0;

        // Gap 检测：缓冲最老 id 已 > lastId + 1 说明中间有事件被 ring 淘汰
        Long oldest = ch.oldestBufferedEventId();
        long serverMax = ch.currentMaxEventId();
        boolean gap;
        if (oldest == null) {
            // 缓冲为空；若服务端已分配过 > lastId 的事件，但都被淘汰，则 gap
            gap = serverMax > lastEventId;
        } else {
            gap = oldest > lastEventId + 1;
        }
        if (gap) {
            try {
                sink.next(AgentStreamEvent.resyncRequired(lastEventId, oldest, serverMax));
            } catch (Exception ex) {
                log.warn("AgentStreamBridge resync emit failed sessionId={}: {}", sessionId, ex.getMessage());
            }
            log.info("AgentStreamBridge replay gap: sessionId={}, clientLast={}, oldest={}, serverMax={}",
                    sessionId, lastEventId, oldest, serverMax);
            return -1;
        }

        List<AgentStreamEvent> replay = ch.snapshotAfter(lastEventId);
        int count = 0;
        for (AgentStreamEvent e : replay) {
            try {
                sink.next(e);
                count++;
            } catch (Exception ex) {
                log.warn("AgentStreamBridge replay failed sessionId={}, eventId={}: {}",
                        sessionId, e.getEventId(), ex.getMessage());
                break;
            }
        }
        if (count > 0) {
            log.info("AgentStreamBridge replay: sessionId={}, from={}, replayed={}",
                    sessionId, lastEventId, count);
        }
        return count;
    }

    /** 获取当前已分配的最大 eventId；客户端 state 端点据此返回 lastEventId。 */
    public long currentMaxEventId(String sessionId) {
        SessionChannel ch = channels.get(sessionId);
        return ch != null ? ch.currentMaxEventId() : 0L;
    }

    /** 当前持有 sink（即客户端在线）的 sessionId 集合；心跳调度器据此决定向谁发心跳。 */
    public Set<String> activeSessions() {
        Set<String> result = ConcurrentHashMap.newKeySet();
        channels.forEach((sid, ch) -> {
            if (ch.currentSink() != null) result.add(sid);
        });
        return result;
    }

    /** 监控用 */
    public int activeSessionCount() {
        return (int) channels.values().stream().filter(ch -> ch.currentSink() != null).count();
    }

    /**
     * 惰性清理：周期性扫描 channels，移除已超过保留期、且当前无 sink 的空闲会话，
     * 防止长期运行后 channels Map 膨胀。仅删除 detach 时间超过 {@code retentionAfterUnregisterMs}
     * 的条目，避免误清短时掉线重连场景。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString =
            "${actionow.agent.stream.buffer.cleanup-interval-ms:60000}")
    public void evictIdleChannels() {
        long now = System.currentTimeMillis();
        channels.entrySet().removeIf(entry -> {
            SessionChannel ch = entry.getValue();
            long detachedAt = ch.detachedAt();
            return ch.currentSink() == null
                    && detachedAt > 0
                    && now - detachedAt > retentionAfterUnregisterMs;
        });
    }

    // ==================== 内部 channel 实现 ====================

    private class SessionChannel {
        private final AtomicLong counter = new AtomicLong(0);
        private final Deque<AgentStreamEvent> buffer = new ArrayDeque<>();
        private volatile FluxSink<AgentStreamEvent> sink;
        private volatile long detachedAt;

        long nextEventId() {
            return counter.incrementAndGet();
        }

        long currentMaxEventId() {
            return counter.get();
        }

        /** 远端 pod 来的事件带自己的 id，把本地计数器推进到不小于该 id，避免本地后续分配重号。 */
        void adoptRemoteEventId(long remoteId) {
            counter.accumulateAndGet(remoteId, Math::max);
        }

        void attachSink(FluxSink<AgentStreamEvent> s) {
            this.sink = s;
            this.detachedAt = 0L;
        }

        void detachSink(FluxSink<AgentStreamEvent> s) {
            if (this.sink == s) {
                this.sink = null;
                this.detachedAt = System.currentTimeMillis();
            }
        }

        FluxSink<AgentStreamEvent> currentSink() {
            return sink;
        }

        long detachedAt() {
            return detachedAt;
        }

        synchronized void bufferEvent(AgentStreamEvent e) {
            long now = System.currentTimeMillis();
            buffer.addLast(e);
            while (buffer.size() > maxBufferSize) {
                buffer.pollFirst();
            }
            // 年龄淘汰：若首个事件已过期则移除
            while (!buffer.isEmpty()) {
                AgentStreamEvent head = buffer.peekFirst();
                long ts = timestampOf(head);
                if (ts > 0 && now - ts > maxAgeMs) {
                    buffer.pollFirst();
                } else {
                    break;
                }
            }
        }

        synchronized List<AgentStreamEvent> snapshotAfter(long lastEventId) {
            List<AgentStreamEvent> result = new ArrayList<>();
            for (AgentStreamEvent e : buffer) {
                if (e.getEventId() != null && e.getEventId() > lastEventId) {
                    result.add(e);
                }
            }
            return result;
        }

        /** 缓冲最老事件的 eventId；缓冲为空返回 null。供 replayAfter 做 gap 检测。 */
        synchronized Long oldestBufferedEventId() {
            AgentStreamEvent head = buffer.peekFirst();
            return head == null ? null : head.getEventId();
        }

        private long timestampOf(AgentStreamEvent e) {
            if (e == null || e.getTimestamp() == null) return 0;
            return e.getTimestamp().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        }
    }
}
