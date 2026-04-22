package com.actionow.common.redis.lock;

import java.util.function.Supplier;

/**
 * 分布式锁接口
 *
 * @author Actionow
 */
public interface DistributedLock {

    /**
     * 尝试获取锁
     *
     * @param key        锁的key
     * @param expireTime 过期时间（秒）
     * @return 是否获取成功
     */
    boolean tryLock(String key, long expireTime);

    /**
     * 释放锁
     *
     * @param key 锁的key
     */
    void unlock(String key);

    /**
     * 在锁内执行操作
     *
     * @param key        锁的key
     * @param expireTime 过期时间（秒）
     * @param supplier   要执行的操作
     * @param <T>        返回类型
     * @return 执行结果
     */
    <T> T executeWithLock(String key, long expireTime, Supplier<T> supplier);

    /**
     * 在锁内执行操作（无返回值）
     *
     * @param key        锁的key
     * @param expireTime 过期时间（秒）
     * @param runnable   要执行的操作
     */
    void executeWithLock(String key, long expireTime, Runnable runnable);
}
