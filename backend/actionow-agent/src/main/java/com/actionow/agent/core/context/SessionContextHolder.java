package com.actionow.agent.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.actionow.agent.core.scope.AgentContext;
import com.actionow.common.core.context.UserContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * 会话上下文的 <b>store-of-record</b>：以 sessionId 为 key 存 {@link UserContext} + {@link AgentContext}。
 *
 * <h2>职责分工</h2>
 * <ul>
 *   <li><b>本类（SessionContextHolder）</b>：Caffeine 有界缓存 + TTL，存储完整上下文；
 *       线程局部只保存 sessionId（TTL），跨 Reactor 线程池自动传播。</li>
 *   <li>{@link com.actionow.agent.core.scope.AgentContextHolder}：
 *       线程局部的 AgentContext view；读取时若 TTL 空会自动回退到本 store。</li>
 * </ul>
 *
 * <h2>唯一写入点</h2>
 * 仅允许 {@link com.actionow.agent.core.scope.AgentContextBuilder#buildAndRegister} 调用 {@link #set}，
 * 保证 store 与 thread view 的一致性。其它地方请不要直接 set。
 *
 * @author Actionow
 */
@Slf4j
public class SessionContextHolder {

    /** 存储上限：单进程最多追踪这么多会话的上下文，防止异常路径遗漏 clear 导致无限膨胀。 */
    private static final int MAX_SESSIONS = 10_000;
    /** 默认过期时间：30 分钟（Caffeine 到期自动驱逐）。 */
    private static volatile long expiryMs = 30 * 60 * 1000L;

    /**
     * 存储每个 Session 的执行上下文（有界 + 自动过期驱逐）。
     */
    private static volatile Cache<String, ExecutionContext> SESSION_CONTEXTS = buildCache(expiryMs);

    private static Cache<String, ExecutionContext> buildCache(long ttlMs) {
        return Caffeine.newBuilder()
                .maximumSize(MAX_SESSIONS)
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .build();
    }

    /**
     * 当前线程的 Session ID（用于工具执行时查找上下文）
     * 使用 TransmittableThreadLocal 支持跨线程池传播
     */
    private static final TransmittableThreadLocal<String> CURRENT_SESSION_ID = new TransmittableThreadLocal<>();

    /**
     * 执行上下文
     */
    @Data
    @Builder
    public static class ExecutionContext {
        private UserContext userContext;
        private AgentContext agentContext;
        private long createdAt;
    }

    /**
     * 存储 Session 上下文
     *
     * @param sessionId    会话 ID
     * @param userContext  用户上下文
     * @param agentContext Agent 上下文
     */
    public static void set(String sessionId, UserContext userContext, AgentContext agentContext) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Cannot store context with null or blank session ID");
            return;
        }

        ExecutionContext ctx = ExecutionContext.builder()
                .userContext(userContext)
                .agentContext(agentContext)
                .createdAt(System.currentTimeMillis())
                .build();

        SESSION_CONTEXTS.put(sessionId, ctx);
        long size = SESSION_CONTEXTS.estimatedSize();
        if (size > MAX_SESSIONS * 0.9) {
            log.warn("SessionContextHolder approaching cap: size={}, max={}", size, MAX_SESSIONS);
        }
        log.debug("Stored context for session: {}, workspaceId={}, tenantSchema={}",
                sessionId,
                userContext != null ? userContext.getWorkspaceId() : null,
                userContext != null ? userContext.getTenantSchema() : null);
    }

    /**
     * 设置当前线程的 Session ID
     * 在工具执行前调用，使工具能够通过 getCurrentContext() 获取上下文
     */
    public static void setCurrentSessionId(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }

    /**
     * 获取当前线程的 Session ID
     */
    public static String getCurrentSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    /**
     * 获取 Session 上下文
     *
     * @param sessionId 会话 ID
     * @return 执行上下文，如果不存在返回 null
     */
    public static ExecutionContext get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return SESSION_CONTEXTS.getIfPresent(sessionId);
    }

    /**
     * 获取当前线程的 Session 上下文。
     * 仅通过 ThreadLocal 中显式设置的 Session ID 查找，不做任何推断，
     * 避免并发场景下返回错误会话的上下文导致跨租户数据泄露。
     */
    public static ExecutionContext getCurrentContext() {
        String sessionId = CURRENT_SESSION_ID.get();
        if (sessionId != null) {
            return SESSION_CONTEXTS.getIfPresent(sessionId);
        }

        long size = SESSION_CONTEXTS.estimatedSize();
        if (size > 0) {
            log.debug("No current session ID set on thread, cannot resolve context (active sessions: {})", size);
        }

        return null;
    }

    /**
     * 获取当前 Session 的 UserContext
     */
    public static UserContext getCurrentUserContext() {
        ExecutionContext ctx = getCurrentContext();
        return ctx != null ? ctx.getUserContext() : null;
    }

    /**
     * 获取当前 Session 的 AgentContext
     */
    public static AgentContext getCurrentAgentContext() {
        ExecutionContext ctx = getCurrentContext();
        return ctx != null ? ctx.getAgentContext() : null;
    }

    /**
     * 清理 Session 上下文
     *
     * @param sessionId 会话 ID
     */
    public static void clear(String sessionId) {
        if (sessionId != null) {
            SESSION_CONTEXTS.invalidate(sessionId);
            log.debug("Cleared context for session: {}", sessionId);
        }
    }

    /**
     * 清理当前线程的 Session ID
     */
    public static void clearCurrentSessionId() {
        CURRENT_SESSION_ID.remove();
    }

    /**
     * 设置过期时间（毫秒）。由外部配置注入；会重建内部 Cache。
     * <p>幂等：相同 ttl 不会触发重建。
     */
    public static synchronized void setExpiryMs(long ms) {
        if (ms == expiryMs) return;
        expiryMs = ms;
        Cache<String, ExecutionContext> rebuilt = buildCache(ms);
        rebuilt.putAll(SESSION_CONTEXTS.asMap());
        SESSION_CONTEXTS = rebuilt;
        log.info("SessionContext expiry 更新为 {}ms，Cache 已重建（保留 {} 条会话）", ms, rebuilt.estimatedSize());
    }

    /**
     * Caffeine 会基于 expireAfterWrite 自动过期并驱逐；此方法仅触发一次主动清理 pass。
     */
    public static void cleanupExpired() {
        SESSION_CONTEXTS.cleanUp();
    }

    /**
     * 获取当前存储的上下文数量（近似值，来自 Caffeine estimatedSize）。
     */
    public static int size() {
        return (int) Math.min(SESSION_CONTEXTS.estimatedSize(), Integer.MAX_VALUE);
    }
}
