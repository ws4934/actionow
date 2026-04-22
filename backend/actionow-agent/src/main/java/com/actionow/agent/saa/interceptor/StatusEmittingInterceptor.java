package com.actionow.agent.saa.interceptor;

import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.interaction.AgentStreamBridge;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 调用阶段 status 拦截器。
 *
 * <p>在每次 LLM 调用前后向当前 session 的 SSE 推 status 事件，
 * 让前端能看到 "思考中..." 的实时反馈，避免长时间空白。
 *
 * <p>phase：
 * <ul>
 *   <li>{@code llm_call} — 调用开始</li>
 *   <li>{@code llm_response} — 调用完成（含 durationMs / iteration）</li>
 * </ul>
 *
 * <p>iteration 为 session 级计数：同一 session 的多次 ReAct LLM 调用依次递增，
 * 不同 session 互不影响。缓存 1 小时后自动过期，避免长生命周期 session 的内存泄漏。
 *
 * @author Actionow
 */
@Slf4j
@RequiredArgsConstructor
public class StatusEmittingInterceptor extends ModelInterceptor {

    private final AgentStreamBridge streamBridge;

    /** session → iteration 计数器；1 小时无写入后过期 */
    private static final Cache<String, AtomicInteger> SESSION_ITERATIONS =
            Caffeine.newBuilder()
                    .expireAfterAccess(1, TimeUnit.HOURS)
                    .maximumSize(10_000)
                    .build();

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler next) {
        String sessionId = SessionContextHolder.getCurrentSessionId();
        int iter = sessionId != null
                ? SESSION_ITERATIONS.get(sessionId, k -> new AtomicInteger(0)).incrementAndGet()
                : 0;
        long start = System.currentTimeMillis();

        if (sessionId != null) {
            try {
                Map<String, Object> details = new HashMap<>();
                details.put("iteration", iter);
                streamBridge.publish(sessionId, AgentStreamEvent.status(
                        "llm_call", "AI 思考中（第 " + iter + " 轮）", null, details));
            } catch (Exception e) {
                log.debug("emit llm_call status failed: {}", e.getMessage());
            }
        }

        try {
            return next.call(request);
        } finally {
            if (sessionId != null) {
                try {
                    long ms = System.currentTimeMillis() - start;
                    Map<String, Object> details = new HashMap<>();
                    details.put("iteration", iter);
                    details.put("durationMs", ms);
                    streamBridge.publish(sessionId, AgentStreamEvent.status(
                            "llm_response", "已完成第 " + iter + " 轮推理", null, details));
                } catch (Exception e) {
                    log.debug("emit llm_response status failed: {}", e.getMessage());
                }
            }
        }
    }

    /** session 结束时由 teardown 显式清理；未清理也会在 1 小时后自然过期 */
    public static void clearSession(String sessionId) {
        if (sessionId != null) {
            SESSION_ITERATIONS.invalidate(sessionId);
        }
    }

    @Override
    public String getName() {
        return "StatusEmittingInterceptor";
    }
}
