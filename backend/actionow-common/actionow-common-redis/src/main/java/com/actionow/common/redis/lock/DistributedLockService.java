package com.actionow.common.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务
 * 基于 Redisson 实现
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    /**
     * 默认等待时间（秒）
     */
    private static final long DEFAULT_WAIT_TIME = 10L;

    /**
     * 默认持有时间（秒）
     */
    private static final long DEFAULT_LEASE_TIME = 30L;

    /**
     * 获取锁
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    /**
     * 尝试加锁
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
    }

    /**
     * 尝试加锁
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放锁
     */
    public void unlock(String lockKey) {
        RLock lock = getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("释放分布式锁: {}", lockKey);
        }
    }

    /**
     * 在锁内执行任务（无返回值）
     */
    public boolean executeWithLock(String lockKey, Runnable task) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS, task);
    }

    /**
     * 在锁内执行任务（无返回值）
     */
    public boolean executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Runnable task) {
        if (tryLock(lockKey, waitTime, leaseTime, unit)) {
            try {
                task.run();
                return true;
            } finally {
                unlock(lockKey);
            }
        }
        log.warn("获取分布式锁失败: {}", lockKey);
        return false;
    }

    /**
     * 在锁内执行任务（有返回值）
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS, supplier);
    }

    /**
     * 在锁内执行任务（有返回值）
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        if (tryLock(lockKey, waitTime, leaseTime, unit)) {
            try {
                return supplier.get();
            } finally {
                unlock(lockKey);
            }
        }
        log.warn("获取分布式锁失败: {}", lockKey);
        return null;
    }

    /**
     * 在锁内执行任务（失败时抛出异常）
     */
    public <T> T executeWithLockOrThrow(String lockKey, Supplier<T> supplier, String errorMessage) {
        T result = executeWithLock(lockKey, supplier);
        if (result == null && !tryLock(lockKey, 0, 0, TimeUnit.SECONDS)) {
            throw new IllegalStateException(errorMessage);
        }
        return result;
    }

    /**
     * 判断是否被锁定
     */
    public boolean isLocked(String lockKey) {
        return getLock(lockKey).isLocked();
    }
}
