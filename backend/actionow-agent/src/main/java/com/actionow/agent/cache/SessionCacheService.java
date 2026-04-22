package com.actionow.agent.cache;

import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.mapper.AgentSessionMapper;
import com.actionow.common.core.exception.BusinessException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session 缓存服务
 * 使用本地缓存减少高频 DB 查询，提升高并发性能
 *
 * 缓存策略：
 * - 使用 Caffeine 高性能本地缓存
 * - 过期时间：5 分钟（可配置）
 * - 最大容量：10000 个 Session（可配置）
 * - 写入后刷新：确保数据最终一致性
 *
 * @author Actionow
 */
@Slf4j
@Service
public class SessionCacheService {

    private final AgentSessionMapper sessionMapper;
    private final Cache<String, AgentSessionEntity> sessionCache;

    // 缓存统计
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public SessionCacheService(AgentSessionMapper sessionMapper,
                               AgentRuntimeConfigService agentRuntimeConfig) {
        this.sessionMapper = sessionMapper;
        int maxSize = agentRuntimeConfig.getSessionCacheMaxSize();
        int expireMinutes = agentRuntimeConfig.getSessionCacheExpireMinutes();
        this.sessionCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(expireMinutes))
                .recordStats()
                .build();
        log.info("SessionCacheService initialized: maxSize={}, expireMinutes={}", maxSize, expireMinutes);
    }

    /**
     * 获取 Session（优先从缓存）
     *
     * @param sessionId 会话 ID
     * @return Session 实体
     * @throws BusinessException 如果 Session 不存在
     */
    public AgentSessionEntity getSession(String sessionId) {
        AgentSessionEntity cached = sessionCache.getIfPresent(sessionId);
        if (cached != null) {
            cacheHits.incrementAndGet();
            log.debug("Session cache HIT: {}", sessionId);
            return cached;
        }

        cacheMisses.incrementAndGet();
        log.debug("Session cache MISS: {}", sessionId);

        // 从数据库加载
        AgentSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null || entity.getIsDeleted()) {
            throw new BusinessException("0709003", "会话不存在");
        }

        // 放入缓存
        sessionCache.put(sessionId, entity);
        return entity;
    }

    /**
     * 获取 Session（优先从缓存，不抛异常）
     *
     * @param sessionId 会话 ID
     * @return Session 实体，不存在返回 null
     */
    public AgentSessionEntity getSessionOrNull(String sessionId) {
        AgentSessionEntity cached = sessionCache.getIfPresent(sessionId);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        AgentSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity != null && !entity.getIsDeleted()) {
            sessionCache.put(sessionId, entity);
        }
        return entity;
    }

    /**
     * 更新缓存
     * 当 Session 发生变更时调用
     *
     * @param entity Session 实体
     */
    public void updateCache(AgentSessionEntity entity) {
        if (entity != null && entity.getId() != null) {
            sessionCache.put(entity.getId(), entity);
            log.debug("Session cache updated: {}", entity.getId());
        }
    }

    /**
     * 失效缓存
     * 当 Session 被删除或状态发生重大变化时调用
     *
     * @param sessionId 会话 ID
     */
    public void invalidate(String sessionId) {
        sessionCache.invalidate(sessionId);
        log.debug("Session cache invalidated: {}", sessionId);
    }

    /**
     * 清空所有缓存
     */
    public void invalidateAll() {
        sessionCache.invalidateAll();
        log.info("Session cache cleared");
    }

    /**
     * 获取缓存命中率
     */
    public double getHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
                cacheHits.get(),
                cacheMisses.get(),
                sessionCache.estimatedSize(),
                getHitRate()
        );
    }

    /**
     * 缓存统计
     */
    public record CacheStats(
            long hits,
            long misses,
            long size,
            double hitRate
    ) {}
}
