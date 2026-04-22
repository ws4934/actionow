package com.actionow.agent.interaction;

import com.actionow.agent.core.agent.AgentStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 跨 pod 事件 fan-out 桥接 — Redis Streams 版本（P3 正式方案）。
 *
 * <h2>定位</h2>
 * 填补 {@link AgentStreamBridge} 的"同 pod 内存"限制：某个 session 的 LLM 执行在 pod A，
 * 客户端重连负载均衡到 pod B 时，pod B 没有 sink 也没有事件缓冲。本组件通过 Redis Streams
 * 把 publish 的事件持久化广播出去，每个 pod 都独立消费流，把事件写入本地 bridge 缓冲 /
 * 推给已挂载的 sink，实现"任一 pod 上都能看到全集群产生的事件"。
 *
 * <h2>为什么从 Pub/Sub 升级到 Streams</h2>
 * <ul>
 *   <li><b>持久化</b>：XADD 写入的事件在 MAXLEN 范围内持久留存，晚起的 pod 也能回看历史；
 *       Pub/Sub 订阅前的消息永久丢失。</li>
 *   <li><b>故障恢复</b>：生产 pod 宕机 / 网络抖动不会让历史事件蒸发，消费者重连后可继续读。</li>
 *   <li><b>容量自管</b>：通过 {@code XADD MAXLEN ~ N} 按条数裁剪，无需额外清理逻辑。</li>
 * </ul>
 *
 * <h2>消费模型</h2>
 * 每个 pod 起一条虚拟线程做 {@code XREAD BLOCK} 循环：
 * <ul>
 *   <li>初始 offset 为 {@code $}（stream 当前尾部）—— 只消费 pod 启动后的事件；
 *       历史事件由 {@code AgentStreamBridge} 本地 ring + DB 消息表兜底。</li>
 *   <li>不使用 consumer group：每个 pod 独立消费全集，保证广播语义。</li>
 *   <li>自回环通过 envelope 里的 {@code podId} 字段过滤，避免本机事件被 republish 放大。</li>
 * </ul>
 *
 * <h2>事件 schema</h2>
 * Stream 每条记录只有一个 field {@code d}，内容是 JSON 序列化的 envelope：
 * <pre>{
 *   "podId": "<source pod uuid>",
 *   "sessionId": "<agent session id>",
 *   "event": { ... AgentStreamEvent 完整字段，含源 pod 分配的 eventId ... }
 * }</pre>
 *
 * <h2>限制</h2>
 * <ul>
 *   <li>每个 pod 启动时从 {@code $} 开始读；启动前的事件不会被本 pod 看到。若业务需要
 *       "无限回放历史" 语义，应改走 XRANGE + 持久化消息表。</li>
 *   <li>MAXLEN 默认 50000；超出会被 Redis 侧裁剪，非常长的会话依赖 DB 对齐。</li>
 * </ul>
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "actionow.agent.stream.fanout.enabled", havingValue = "true")
public class AgentStreamRedisFanout {

    /** 全集群共享的单一 stream key — 所有 agent 事件写入此键。 */
    static final String STREAM_KEY = "actionow:agent:stream:events";
    /** stream record 内唯一字段名 —— JSON envelope 载荷。 */
    static final String FIELD_DATA = "d";

    /** pod 唯一标识，用于消息自回环去重。 */
    private final String podId = UUID.randomUUID().toString();

    private final AgentStreamBridge streamBridge;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Stream 最大保留条数（近似裁剪，ε 允许 Redis 按 macronode 边界优化）。 */
    @Value("${actionow.agent.stream.fanout.maxlen:50000}")
    private long maxLen;

    /** XREAD BLOCK 毫秒数；越短越快响应 shutdown，越长越省网络。 */
    @Value("${actionow.agent.stream.fanout.block-ms:5000}")
    private long blockMs;

    /** XREAD 每次最大拉取条数。 */
    @Value("${actionow.agent.stream.fanout.read-count:100}")
    private int readCount;

    private volatile Thread consumerThread;
    private volatile boolean running;

    @PostConstruct
    public void init() {
        running = true;
        consumerThread = Thread.ofVirtual()
                .name("agent-fanout-" + podId.substring(0, 8))
                .start(this::consumeLoop);
        log.info("AgentStreamRedisFanout (Streams) enabled podId={}, key={}, maxLen={}",
                podId, STREAM_KEY, maxLen);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        Thread t = consumerThread;
        if (t != null) t.interrupt();
    }

    /**
     * 把事件跨 pod 广播 —— 由 {@link AgentStreamBridge#publish} 在本地分发后调用。
     * 内部用 {@code XADD MAXLEN ~ N} 追加；失败仅 WARN，不影响本 pod 的 SSE。
     */
    public void broadcast(String sessionId, AgentStreamEvent event) {
        if (sessionId == null || event == null) return;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("podId", podId);
            envelope.put("sessionId", sessionId);
            envelope.put("event", event);
            String json = objectMapper.writeValueAsString(envelope);

            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(STREAM_KEY)
                    .ofMap(Map.of(FIELD_DATA, json));
            StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
            ops.add(record, XAddOptions.maxlen(maxLen).approximateTrimming(true));
        } catch (Exception e) {
            log.warn("Fanout broadcast failed sessionId={}, type={}: {}",
                    sessionId, event.getType(), e.getMessage());
        }
    }

    /**
     * XREAD BLOCK 循环 —— 在虚拟线程上运行，处理 Redis 抛出的任何瞬时异常并退避重试。
     */
    private void consumeLoop() {
        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
        ReadOffset offset = ReadOffset.latest(); // '$' — 只消费 pod 启动后到达的事件
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                StreamOffset<String> from = StreamOffset.create(STREAM_KEY, offset);
                List<MapRecord<String, String, String>> records = ops.read(
                        StreamReadOptions.empty()
                                .block(Duration.ofMillis(blockMs))
                                .count(readCount),
                        from);
                if (records == null || records.isEmpty()) continue;
                RecordId lastId = null;
                for (MapRecord<String, String, String> rec : records) {
                    try {
                        String json = rec.getValue().get(FIELD_DATA);
                        if (json != null) processIncoming(json);
                    } catch (Exception e) {
                        log.warn("Fanout process record id={} failed: {}", rec.getId(), e.getMessage());
                    }
                    lastId = rec.getId();
                }
                if (lastId != null) {
                    offset = ReadOffset.from(lastId);
                }
            } catch (Exception e) {
                if (!running) break;
                log.warn("Fanout XREAD loop error: {}", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("AgentStreamRedisFanout consumer loop exited podId={}", podId);
    }

    /**
     * 解包 envelope 并把远端事件 republish 到本地 bridge；自回环直接丢弃。
     */
    private void processIncoming(String json) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = objectMapper.readValue(json, Map.class);
        String sourcePod = String.valueOf(envelope.get("podId"));
        if (podId.equals(sourcePod)) return;
        String sessionId = (String) envelope.get("sessionId");
        AgentStreamEvent event = objectMapper.convertValue(envelope.get("event"), AgentStreamEvent.class);
        if (sessionId == null || event == null) return;
        // 关键：eventId 已由源 pod 分配；republish 保持 id 不变，
        // 避免同一事件在不同 pod 被分配不同本地 id、破坏客户端 lastEventId 单调性。
        streamBridge.republish(sessionId, event);
    }
}
