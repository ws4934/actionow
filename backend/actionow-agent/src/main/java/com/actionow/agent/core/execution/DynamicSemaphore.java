package com.actionow.agent.core.execution;

import java.util.concurrent.Semaphore;

/**
 * 动态可调整的 Semaphore
 * 支持运行时调整最大许可数，不中断正在执行的操作
 *
 * 通过继承 Semaphore 访问 protected reducePermits() 方法实现缩容
 *
 * @author Actionow
 */
public class DynamicSemaphore extends Semaphore {

    private volatile int maxPermits;

    public DynamicSemaphore(int permits) {
        super(permits, true); // fair mode
        this.maxPermits = permits;
    }

    /**
     * 调整最大许可数
     * - 增加许可：立即 release(delta)
     * - 减少许可：调用 reducePermits(delta)，不中断正在执行的操作，自然收敛
     *
     * @param newMax 新的最大许可数
     */
    public synchronized void resize(int newMax) {
        if (newMax < 1) {
            throw new IllegalArgumentException("Max permits must be >= 1, got: " + newMax);
        }
        int delta = newMax - maxPermits;
        if (delta > 0) {
            release(delta);
        } else if (delta < 0) {
            reducePermits(-delta);
        }
        maxPermits = newMax;
    }

    public int getMaxPermits() {
        return maxPermits;
    }
}
