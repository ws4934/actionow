package com.actionow.common.redis.lock;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 分布式锁实现
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock implements DistributedLock {

    private final StringRedisTemplate redisTemplate;

    /**
     * 线程本地存储锁的值
     */
    private static final ThreadLocal<String> LOCK_VALUE = new ThreadLocal<>();

    /**
     * 解锁 Lua 脚本（保证原子性）
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end";

    @Override
    public boolean tryLock(String key, long expireTime) {
        String value = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, expireTime, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(success)) {
            LOCK_VALUE.set(value);
            log.debug("获取分布式锁成功: key={}", key);
            return true;
        }

        log.debug("获取分布式锁失败: key={}", key);
        return false;
    }

    @Override
    public void unlock(String key) {
        String value = LOCK_VALUE.get();
        if (value == null) {
            log.warn("尝试释放未持有的锁: key={}", key);
            return;
        }

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(key), value);

            if (result != null && result == 1) {
                log.debug("释放分布式锁成功: key={}", key);
            } else {
                log.warn("释放分布式锁失败（锁可能已过期）: key={}", key);
            }
        } finally {
            LOCK_VALUE.remove();
        }
    }

    @Override
    public <T> T executeWithLock(String key, long expireTime, Supplier<T> supplier) {
        int retryCount = 0;
        int maxRetry = 3;
        long retryInterval = 100; // 毫秒

        while (retryCount < maxRetry) {
            if (tryLock(key, expireTime)) {
                try {
                    return supplier.get();
                } finally {
                    unlock(key);
                }
            }

            retryCount++;
            if (retryCount < maxRetry) {
                try {
                    Thread.sleep(retryInterval * retryCount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "获取分布式锁被中断");
                }
            }
        }

        throw new BusinessException(ResultCode.RATE_LIMITED, "系统繁忙，请稍后重试");
    }

    @Override
    public void executeWithLock(String key, long expireTime, Runnable runnable) {
        executeWithLock(key, expireTime, () -> {
            runnable.run();
            return null;
        });
    }
}
