package com.actionow.agent.interaction;

import com.actionow.agent.metrics.AgentMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HITL 阻塞-唤醒协调器。
 *
 * <p>工具线程（在 {@code ask_user_*} 工具内）调用 {@link #awaitAnswer} 创建一个 pending ask，
 * 并阻塞当前线程；HTTP controller 收到用户回答后调 {@link #submitAnswer} 唤醒。
 *
 * <h2>跨 Pod 策略</h2>
 * 当前实现为**本地 JVM 内存 Map**，适用于单实例或前端走 sticky session 的多实例场景。
 * 若部署多 Pod 且前端未绑定实例，需要升级为 Redis Pub/Sub 广播唤醒——预留了 {@link #redisTemplate}
 * 和 TTL 写入，后续扩展即可不破坏调用方 API。
 *
 * <h2>失败模式</h2>
 * <ul>
 *   <li>超时 — {@link #awaitAnswer} 抛 {@link TimeoutException}</li>
 *   <li>pending ask 已存在（重复 askId）— 抛 {@link IllegalStateException}</li>
 *   <li>未知 askId 的 submit — 记 WARN 并忽略（可能是已超时/取消）</li>
 * </ul>
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInteractionService implements MessageListener {

    private static final String REDIS_ANSWER_KEY_PREFIX = "agent:ask:answer:";
    private static final String NOTIFY_CHANNEL = "agent:ask:notify";
    private static final Duration REDIS_ANSWER_TTL = Duration.ofMinutes(10);
    /** 兜底轮询周期：Pub/Sub 丢消息时仍能在 15 秒内唤醒 */
    private static final long FALLBACK_POLL_INTERVAL_SECONDS = 15;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;
    private final AgentMetrics metrics;
    private final AgentAskHistoryService askHistoryService;

    /** askId → ask 创建时间戳（ms），用于计算 round-trip 耗时 */
    private final Map<String, Long> askStartTimes = new ConcurrentHashMap<>();

    /** 本地 pending Future 表：askId → Future。由 awaitAnswer 写入，submitAnswer / cancel 读取。 */
    private final Map<String, CompletableFuture<UserAnswer>> pendingAsks = new ConcurrentHashMap<>();

    /** 反向索引 sessionId → askIds，供 session 取消时级联取消所有 pending ask */
    private final Map<String, Set<String>> sessionAsks = new ConcurrentHashMap<>();

    /** 单线程兜底轮询器；@PostConstruct 启动，@PreDestroy 关闭 */
    private ScheduledExecutorService fallbackPoller;

    /**
     * 启动时订阅 {@link #NOTIFY_CHANNEL}。
     * <p>多 Pod 部署时，answer 提交可能命中任意 Pod，但发起 ask 的工具线程在另一 Pod 上等待。
     * 通过 Pub/Sub 把 askId 广播到所有 Pod，每个 Pod 检查本地 pendingAsks，命中则从 Redis 拉 answer 完成 future。
     */
    @PostConstruct
    public void subscribeNotifications() {
        listenerContainer.addMessageListener(this, new ChannelTopic(NOTIFY_CHANNEL));
        log.info("UserInteractionService 已订阅 Redis 频道 {}（跨 Pod HITL 唤醒）", NOTIFY_CHANNEL);

        fallbackPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hitl-fallback-poller");
            t.setDaemon(true);
            return t;
        });
        fallbackPoller.scheduleAtFixedRate(this::pollPendingAsks,
                FALLBACK_POLL_INTERVAL_SECONDS, FALLBACK_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("HITL 兜底轮询已启动（周期 {}s）", FALLBACK_POLL_INTERVAL_SECONDS);

        metrics.registerPendingAsksGauge(this::pendingCount);
    }

    @PreDestroy
    public void shutdown() {
        if (fallbackPoller != null) {
            fallbackPoller.shutdownNow();
        }
    }

    /**
     * 定期扫描本地 pendingAsks，对每个仍未完成的 askId 查 Redis answer 兜底。
     * <p>覆盖场景：submitAnswer 的 Pub/Sub convertAndSend 失败 / 订阅临时断开 / 本 Pod onMessage 处理异常。
     */
    private void pollPendingAsks() {
        if (pendingAsks.isEmpty()) return;
        for (Map.Entry<String, CompletableFuture<UserAnswer>> entry : pendingAsks.entrySet()) {
            String askId = entry.getKey();
            CompletableFuture<UserAnswer> future = entry.getValue();
            if (future.isDone()) continue;
            try {
                String json = redisTemplate.opsForValue().get(REDIS_ANSWER_KEY_PREFIX + askId);
                if (json == null || json.isEmpty()) continue;
                UserAnswer answer = objectMapper.readValue(json, UserAnswer.class);
                if (future.complete(answer)) {
                    log.info("HITL 兜底轮询唤醒成功: askId={}（Pub/Sub 可能丢消息）", askId);
                }
            } catch (Exception e) {
                log.debug("HITL 兜底轮询 askId={} 异常: {}", askId, e.getMessage());
            }
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String askId = new String(message.getBody(), StandardCharsets.UTF_8);
        if (askId == null || askId.isBlank()) return;
        CompletableFuture<UserAnswer> future = pendingAsks.get(askId);
        if (future == null) {
            // 该 ask 不属于本 Pod，正常情况
            return;
        }
        MDC.put("askId", askId);
        try {
            String json = redisTemplate.opsForValue().get(REDIS_ANSWER_KEY_PREFIX + askId);
            if (json == null || json.isEmpty()) {
                log.warn("跨 Pod HITL 通知收到 askId={}，但 Redis 没有对应 answer（可能被 TTL 或 cancel 清掉）", askId);
                return;
            }
            UserAnswer answer = objectMapper.readValue(json, UserAnswer.class);
            boolean completed = future.complete(answer);
            log.info("跨 Pod HITL 唤醒成功: askId={}, completed={}", askId, completed);
        } catch (Exception e) {
            log.warn("跨 Pod HITL 唤醒失败 askId={}: {}", askId, e.getMessage());
        } finally {
            MDC.remove("askId");
        }
    }

    /** 生成新的 askId（短 UUID）。 */
    public String newAskId() {
        return "ask-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 工具线程调用：注册一个 pending ask 并阻塞等待用户回答。
     *
     * @param sessionId 会话 ID（用于日志 + 后续跨 Pod 路由）
     * @param askId     本次 ask 的唯一 ID，来自 {@link #newAskId()}
     * @param timeout   最长等待时间；超时抛 {@link TimeoutException}
     * @return 用户的答案载荷
     * @throws TimeoutException     等待超过 timeout 时抛出
     * @throws InterruptedException 工具线程被中断（通常是 session 取消）
     * @throws IllegalStateException 重复的 askId
     */
    public UserAnswer awaitAnswer(String sessionId, String askId, Duration timeout)
            throws TimeoutException, InterruptedException {
        CompletableFuture<UserAnswer> future = new CompletableFuture<>();
        CompletableFuture<UserAnswer> previous = pendingAsks.putIfAbsent(askId, future);
        if (previous != null) {
            throw new IllegalStateException("askId 已存在（可能并发重复）: " + askId);
        }

        log.info("HITL ask 注册: sessionId={}, askId={}, timeout={}s",
                sessionId, askId, timeout.toSeconds());

        long startMs = System.currentTimeMillis();
        askStartTimes.put(askId, startMs);
        if (sessionId != null) {
            sessionAsks.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(askId);
        }
        metrics.recordAskUserCreated();

        try {
            UserAnswer answer = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            metrics.recordAskUserAnswered(System.currentTimeMillis() - startMs);
            return answer;
        } catch (TimeoutException te) {
            metrics.recordAskUserTimeout(System.currentTimeMillis() - startMs);
            askHistoryService.recordFinal(askId, "TIMEOUT", null);
            throw te;
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            throw new RuntimeException("ask 等待被异常终止: " + cause.getMessage(), cause);
        } finally {
            pendingAsks.remove(askId);
            askStartTimes.remove(askId);
            if (sessionId != null) {
                Set<String> ids = sessionAsks.get(sessionId);
                if (ids != null) {
                    ids.remove(askId);
                    if (ids.isEmpty()) sessionAsks.remove(sessionId);
                }
            }
        }
    }

    /**
     * HTTP controller 调用：提交用户答案并唤醒等待线程。
     *
     * <p>即使本地 Future 不存在（例如 SSE 断连后重连到另一个 Pod），也会把答案写到 Redis（TTL 10 分钟），
     * 以便后续跨 Pod 唤醒机制接管；本期本地 Map 命中则立即 complete。
     */
    public void submitAnswer(String sessionId, String askId, UserAnswer answer) {
        if (answer == null) {
            answer = UserAnswer.builder().rejected(true).build();
        }
        MDC.put("askId", askId);
        if (sessionId != null) MDC.put("sessionId", sessionId);
        try {
            // 1. 写 Redis（10 分钟 TTL）；其它 Pod 收到 Pub/Sub 后会从这里拉 answer
            try {
                String json = objectMapper.writeValueAsString(answer);
                redisTemplate.opsForValue().set(REDIS_ANSWER_KEY_PREFIX + askId, json, REDIS_ANSWER_TTL);
            } catch (JsonProcessingException e) {
                log.warn("HITL answer 写入 Redis 失败 sessionId={}, askId={}: {}", sessionId, askId, e.getMessage());
            }

            // 2. 本地 fast path：当前 Pod 直接命中 future
            CompletableFuture<UserAnswer> future = pendingAsks.get(askId);
            if (future != null) {
                boolean completed = future.complete(answer);
                log.info("HITL ask 本地唤醒: sessionId={}, askId={}, completed={}, rejected={}",
                        sessionId, askId, completed, Boolean.TRUE.equals(answer.getRejected()));
            } else {
                log.debug("HITL submit 在本 Pod 未找到 pending ask，将通过 Pub/Sub 广播给其它 Pod: sessionId={}, askId={}",
                        sessionId, askId);
            }

            // 3. Pub/Sub 广播 askId，让其它 Pod 上的等待线程也能被唤醒（即使本地已唤醒，广播也是无害的——
            //    其它 Pod 的 onMessage 找不到本地 future 时直接 noop）
            try {
                redisTemplate.convertAndSend(NOTIFY_CHANNEL, askId);
            } catch (Exception e) {
                log.warn("HITL Pub/Sub 广播失败 askId={}: {}", askId, e.getMessage());
            }

            // 4. 审计终态（ANSWERED / REJECTED）；多 Pod 场景由收到 submit 的那一 Pod 统一落库
            String status = Boolean.TRUE.equals(answer.getRejected()) ? "REJECTED" : "ANSWERED";
            askHistoryService.recordFinal(askId, status, toAnswerMap(answer));
        } finally {
            MDC.remove("askId");
            MDC.remove("sessionId");
        }
    }

    private Map<String, Object> toAnswerMap(UserAnswer answer) {
        if (answer == null) return null;
        Map<String, Object> m = new HashMap<>();
        if (answer.getAnswer() != null) m.put("answer", answer.getAnswer());
        if (answer.getMultiAnswer() != null) m.put("multiAnswer", answer.getMultiAnswer());
        if (answer.getRejected() != null) m.put("rejected", answer.getRejected());
        if (answer.getExtras() != null) m.put("extras", answer.getExtras());
        return m.isEmpty() ? null : m;
    }

    /**
     * 取消一个 pending ask（例如 session 被用户 cancel）。
     * 触发 awaitAnswer 抛 {@link InterruptedException}-风格的异常结束。
     */
    public void cancelAsk(String sessionId, String askId, String reason) {
        CompletableFuture<UserAnswer> future = pendingAsks.remove(askId);
        askStartTimes.remove(askId);
        if (sessionId != null) {
            Set<String> ids = sessionAsks.get(sessionId);
            if (ids != null) {
                ids.remove(askId);
                if (ids.isEmpty()) sessionAsks.remove(sessionId);
            }
        }
        if (future != null) {
            future.completeExceptionally(
                    new RuntimeException("HITL ask 被取消: " + (reason != null ? reason : "unknown")));
            metrics.recordAskUserCancelled();
            log.info("HITL ask 已取消: sessionId={}, askId={}, reason={}", sessionId, askId, reason);
        }
        redisTemplate.delete(REDIS_ANSWER_KEY_PREFIX + askId);
        askHistoryService.recordFinal(askId, "CANCELLED", null);
    }

    /** 监控 / 调试用：当前 pending 数量 */
    public int pendingCount() {
        return pendingAsks.size();
    }

    /**
     * 级联取消：当会话被取消（{@code POST /sessions/{id}/cancel}）时，
     * 把该 session 名下所有 pending ask 一次性结束，避免工具线程持续阻塞至超时。
     *
     * @return 本次实际取消的 ask 数量
     */
    public int cancelAllBySessionId(String sessionId, String reason) {
        if (sessionId == null) return 0;
        Set<String> ids = sessionAsks.remove(sessionId);
        if (ids == null || ids.isEmpty()) return 0;
        int cancelled = 0;
        for (String askId : ids) {
            CompletableFuture<UserAnswer> future = pendingAsks.remove(askId);
            askStartTimes.remove(askId);
            if (future != null && future.completeExceptionally(
                    new RuntimeException("HITL ask 被级联取消: " + (reason != null ? reason : "session cancelled")))) {
                metrics.recordAskUserCancelled();
                cancelled++;
            }
            redisTemplate.delete(REDIS_ANSWER_KEY_PREFIX + askId);
            askHistoryService.recordFinal(askId, "CANCELLED", null);
        }
        if (cancelled > 0) {
            log.info("HITL session 取消级联: sessionId={}, cancelledAsks={}, reason={}", sessionId, cancelled, reason);
        }
        return cancelled;
    }
}
