package com.actionow.common.redis.limiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式限流器
 * 基于 Redisson RRateLimiter 实现
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedissonClient redissonClient;

    /**
     * 限流器缓存
     */
    private final ConcurrentHashMap<String, RRateLimiter> limiterCache = new ConcurrentHashMap<>();

    /**
     * 尝试获取令牌
     *
     * @param key        限流键
     * @param rate       每个时间间隔的令牌数
     * @param rateInterval 时间间隔（秒）
     * @return 是否获取成功
     */
    public boolean tryAcquire(String key, long rate, long rateInterval) {
        RRateLimiter limiter = getOrCreateLimiter(key, rate, rateInterval);
        boolean acquired = limiter.tryAcquire();
        if (!acquired) {
            log.debug("限流触发: key={}, rate={}/{} seconds", key, rate, rateInterval);
        }
        return acquired;
    }

    /**
     * 尝试获取指定数量的令牌
     */
    public boolean tryAcquire(String key, long rate, long rateInterval, int permits) {
        RRateLimiter limiter = getOrCreateLimiter(key, rate, rateInterval);
        boolean acquired = limiter.tryAcquire(permits);
        if (!acquired) {
            log.debug("限流触发: key={}, rate={}/{} seconds, permits={}", key, rate, rateInterval, permits);
        }
        return acquired;
    }

    /**
     * 检查是否会被限流（不消耗令牌）
     */
    public long availablePermits(String key) {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        return limiter.availablePermits();
    }

    /**
     * 删除限流器
     */
    public void deleteLimiter(String key) {
        limiterCache.remove(key);
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.delete();
        log.debug("删除限流器: {}", key);
    }

    /**
     * 获取或创建限流器
     */
    private RRateLimiter getOrCreateLimiter(String key, long rate, long rateInterval) {
        return limiterCache.computeIfAbsent(key, k -> {
            RRateLimiter limiter = redissonClient.getRateLimiter(k);
            // 设置限流规则：每 rateInterval 秒产生 rate 个令牌
            limiter.trySetRate(RateType.OVERALL, rate, rateInterval, RateIntervalUnit.SECONDS);
            log.debug("创建限流器: key={}, rate={}/{} seconds", k, rate, rateInterval);
            return limiter;
        });
    }

    // ==================== 预设限流策略 ====================

    /**
     * API 限流（每秒）
     */
    public boolean tryAcquirePerSecond(String key, long permitsPerSecond) {
        return tryAcquire(key, permitsPerSecond, 1);
    }

    /**
     * API 限流（每分钟）
     */
    public boolean tryAcquirePerMinute(String key, long permitsPerMinute) {
        return tryAcquire(key, permitsPerMinute, 60);
    }

    /**
     * API 限流（每小时）
     */
    public boolean tryAcquirePerHour(String key, long permitsPerHour) {
        return tryAcquire(key, permitsPerHour, 3600);
    }

    /**
     * API 限流（每天）
     */
    public boolean tryAcquirePerDay(String key, long permitsPerDay) {
        return tryAcquire(key, permitsPerDay, 86400);
    }
}
