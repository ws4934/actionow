package com.actionow.task.service;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量作业扫描状态
 * 用于在 batch 仅等待已提交任务完成时，对定时扫描做短暂冷却，避免空转扫表。
 */
@Service
public class BatchJobScanStateService {

    private final Map<String, Long> waitingBatchNextScanAt = new ConcurrentHashMap<>();
    private volatile long globalNextScanAt = 0L;

    public boolean shouldSkip(String batchJobId) {
        Long nextScanAt = waitingBatchNextScanAt.get(batchJobId);
        if (nextScanAt == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now >= nextScanAt) {
            waitingBatchNextScanAt.remove(batchJobId, nextScanAt);
            return false;
        }
        return true;
    }

    public void markWaiting(String batchJobId, long cooldownMs) {
        long nextScanAt = System.currentTimeMillis() + Math.max(cooldownMs, 0L);
        waitingBatchNextScanAt.put(batchJobId, nextScanAt);
    }

    public void markActive(String batchJobId) {
        waitingBatchNextScanAt.remove(batchJobId);
        globalNextScanAt = 0L;
    }

    public void clear(String batchJobId) {
        waitingBatchNextScanAt.remove(batchJobId);
        globalNextScanAt = 0L;
    }

    public boolean shouldSkipGlobalScan() {
        long nextScanAt = globalNextScanAt;
        if (nextScanAt <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() >= nextScanAt) {
            globalNextScanAt = 0L;
            return false;
        }
        return true;
    }

    public void markGlobalWaiting(long cooldownMs) {
        globalNextScanAt = System.currentTimeMillis() + Math.max(cooldownMs, 0L);
    }

    public long getMinRemainingDelayMs(Collection<String> batchJobIds) {
        long now = System.currentTimeMillis();
        long minDelay = Long.MAX_VALUE;
        for (String batchJobId : batchJobIds) {
            Long nextScanAt = waitingBatchNextScanAt.get(batchJobId);
            if (nextScanAt == null) {
                continue;
            }
            long remaining = nextScanAt - now;
            if (remaining <= 0L) {
                waitingBatchNextScanAt.remove(batchJobId, nextScanAt);
                return 0L;
            }
            if (remaining < minDelay) {
                minDelay = remaining;
            }
        }
        return minDelay == Long.MAX_VALUE ? 0L : minDelay;
    }
}
