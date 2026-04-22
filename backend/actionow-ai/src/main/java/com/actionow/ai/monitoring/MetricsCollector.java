package com.actionow.ai.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 执行指标收集服务
 * 收集和聚合AI执行相关的指标数据
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCollector {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String METRICS_PREFIX = "actionow:metrics:";
    private static final String DAILY_STATS_KEY = METRICS_PREFIX + "daily:";
    private static final String PROVIDER_STATS_KEY = METRICS_PREFIX + "provider:";
    private static final String HOURLY_STATS_KEY = METRICS_PREFIX + "hourly:";

    /** 最大指标 key 数量上限（防止基数爆炸） */
    private static final int MAX_METRIC_KEYS = 200;

    /** 脚本阶段白名单 */
    private static final Set<String> ALLOWED_PHASES = Set.of(
            "REQUEST_BUILDER", "RESPONSE_MAPPER", "CUSTOM_LOGIC");

    // 内存中的实时统计（用于高频访问）
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> histograms = new ConcurrentHashMap<>();

    /**
     * 记录执行开始
     */
    public void recordExecutionStart(ExecutionMetrics metrics) {
        incrementCounter("executions.started");
        incrementCounter("executions.started.provider." + metrics.getProviderId());

        if (metrics.getWorkspaceId() != null) {
            incrementCounter("executions.started.workspace." + metrics.getWorkspaceId());
        }

        log.debug("记录执行开始: executionId={}, providerId={}",
                metrics.getExecutionId(), metrics.getProviderId());
    }

    /**
     * 记录执行完成
     */
    @Async
    public void recordExecutionComplete(ExecutionMetrics metrics) {
        try {
            // 更新计数器
            if (metrics.isSuccess()) {
                incrementCounter("executions.success");
                incrementCounter("executions.success.provider." + metrics.getProviderId());
            } else if (metrics.isFailed()) {
                incrementCounter("executions.failed");
                incrementCounter("executions.failed.provider." + metrics.getProviderId());
                incrementCounter("executions.failed.error." + sanitizeErrorCode(metrics.getErrorCode()));
            }

            // 记录耗时
            if (metrics.getTotalDurationMs() != null) {
                recordHistogram("executions.duration", metrics.getTotalDurationMs());
                recordHistogram("executions.duration.provider." + metrics.getProviderId(),
                        metrics.getTotalDurationMs());
            }

            // 记录积分消耗
            if (metrics.getCreditsCost() != null && metrics.getCreditsCost() > 0) {
                incrementCounter("credits.consumed", metrics.getCreditsCost());
            }

            // 记录文件大小
            if (metrics.getOutputFileSize() != null && metrics.getOutputFileSize() > 0) {
                incrementCounter("output.bytes", metrics.getOutputFileSize());
            }

            // 持久化到Redis（每日统计）
            persistDailyStats(metrics);

            // 持久化提供商统计
            persistProviderStats(metrics);

            log.debug("记录执行完成: executionId={}, success={}, durationMs={}",
                    metrics.getExecutionId(), metrics.isSuccess(), metrics.getTotalDurationMs());

        } catch (Exception e) {
            log.error("记录执行指标失败: executionId={}", metrics.getExecutionId(), e);
        }
    }

    /**
     * 记录脚本执行阶段耗时
     */
    public void recordScriptPhase(String executionId, String phase, long durationMs) {
        String safePhase = ALLOWED_PHASES.contains(phase) ? phase : "UNKNOWN";
        recordHistogram("script.phase." + safePhase, durationMs);
        log.debug("记录脚本阶段: executionId={}, phase={}, durationMs={}",
                executionId, safePhase, durationMs);
    }

    /**
     * 记录API调用
     */
    public void recordApiCall(String executionId, String endpoint, long durationMs, boolean success) {
        incrementCounter("api.calls");
        if (success) {
            incrementCounter("api.calls.success");
        } else {
            incrementCounter("api.calls.failed");
        }
        recordHistogram("api.duration", durationMs);
    }

    /**
     * 增加计数器
     */
    public void incrementCounter(String name) {
        incrementCounter(name, 1);
    }

    /**
     * 增加计数器（指定增量）
     */
    public void incrementCounter(String name, long delta) {
        if (counters.size() >= MAX_METRIC_KEYS && !counters.containsKey(name)) {
            counters.computeIfAbsent("_overflow", k -> new LongAdder()).add(delta);
            return;
        }
        counters.computeIfAbsent(name, k -> new LongAdder()).add(delta);
    }

    /**
     * 设置gauge值
     */
    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    /**
     * 记录直方图数据
     */
    public void recordHistogram(String name, long value) {
        if (histograms.size() >= MAX_METRIC_KEYS && !histograms.containsKey(name)) {
            log.debug("直方图 key 数量已达上限 {}，丢弃新 key: {}", MAX_METRIC_KEYS, name);
            return;
        }
        histograms.computeIfAbsent(name, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(value);
        // 限制大小防止内存溢出
        List<Long> list = histograms.get(name);
        if (list.size() > 10000) {
            synchronized (list) {
                if (list.size() > 10000) {
                    list.subList(0, 5000).clear();
                }
            }
        }
    }

    /**
     * 获取计数器值
     */
    public long getCounter(String name) {
        LongAdder adder = counters.get(name);
        return adder != null ? adder.sum() : 0;
    }

    /**
     * 获取gauge值
     */
    public long getGauge(String name) {
        AtomicLong gauge = gauges.get(name);
        return gauge != null ? gauge.get() : 0;
    }

    /**
     * 获取直方图统计
     */
    public HistogramStats getHistogramStats(String name) {
        List<Long> values = histograms.get(name);
        if (values == null || values.isEmpty()) {
            return HistogramStats.empty();
        }

        synchronized (values) {
            List<Long> copy = new ArrayList<>(values);
            Collections.sort(copy);

            long sum = copy.stream().mapToLong(Long::longValue).sum();
            long count = copy.size();
            double avg = (double) sum / count;
            long min = copy.get(0);
            long max = copy.get(copy.size() - 1);
            long p50 = copy.get((int) (copy.size() * 0.5));
            long p90 = copy.get((int) (copy.size() * 0.9));
            long p99 = copy.get((int) (copy.size() * 0.99));

            return new HistogramStats(count, sum, avg, min, max, p50, p90, p99);
        }
    }

    /**
     * 获取所有计数器
     */
    public Map<String, Long> getAllCounters() {
        Map<String, Long> result = new HashMap<>();
        counters.forEach((k, v) -> result.put(k, v.sum()));
        return result;
    }

    /**
     * 获取今日统计
     */
    public Map<String, Object> getTodayStats() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String key = DAILY_STATS_KEY + today;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        Map<String, Object> result = new HashMap<>();
        entries.forEach((k, v) -> result.put(k.toString(), parseValue(v.toString())));
        return result;
    }

    /**
     * 获取提供商统计
     */
    public Map<String, Object> getProviderStats(String providerId) {
        String key = PROVIDER_STATS_KEY + providerId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        Map<String, Object> result = new HashMap<>();
        entries.forEach((k, v) -> result.put(k.toString(), parseValue(v.toString())));
        return result;
    }

    /**
     * 重置内存中的统计（用于测试或定期清理）
     */
    public void resetInMemoryStats() {
        counters.clear();
        gauges.clear();
        histograms.clear();
        log.info("内存统计已重置");
    }

    // ==================== 私有方法 ====================

    /**
     * 持久化每日统计到Redis（使用 Pipeline 批量操作）
     */
    private void persistDailyStats(ExecutionMetrics metrics) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String key = DAILY_STATS_KEY + today;

        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.opsForHash().increment(key, "total", 1);

                if (metrics.isSuccess()) {
                    operations.opsForHash().increment(key, "success", 1);
                } else {
                    operations.opsForHash().increment(key, "failed", 1);
                }

                if (metrics.getTotalDurationMs() != null) {
                    operations.opsForHash().increment(key, "totalDurationMs", metrics.getTotalDurationMs());
                }

                if (metrics.getCreditsCost() != null) {
                    operations.opsForHash().increment(key, "creditsConsumed", metrics.getCreditsCost());
                }

                operations.expire(key, Duration.ofDays(7));
                return null;
            }
        });
    }

    /**
     * 持久化提供商统计到Redis（使用 Pipeline 批量操作）
     */
    private void persistProviderStats(ExecutionMetrics metrics) {
        if (metrics.getProviderId() == null) {
            return;
        }

        String key = PROVIDER_STATS_KEY + metrics.getProviderId();

        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.opsForHash().increment(key, "total", 1);

                if (metrics.isSuccess()) {
                    operations.opsForHash().increment(key, "success", 1);
                } else {
                    operations.opsForHash().increment(key, "failed", 1);
                }

                if (metrics.getTotalDurationMs() != null) {
                    operations.opsForHash().increment(key, "totalDurationMs", metrics.getTotalDurationMs());
                }

                operations.opsForHash().put(key, "lastExecutionTime",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                return null;
            }
        });
    }

    /**
     * 清理错误码（防止Redis key爆炸）
     */
    private String sanitizeErrorCode(String errorCode) {
        if (errorCode == null) {
            return "UNKNOWN";
        }
        // 只保留字母数字和下划线，最多50字符
        return errorCode.replaceAll("[^a-zA-Z0-9_]", "_").substring(0, Math.min(50, errorCode.length()));
    }

    /**
     * 解析Redis中的值
     */
    private Object parseValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * 直方图统计结果
     */
    public record HistogramStats(
            long count,
            long sum,
            double avg,
            long min,
            long max,
            long p50,
            long p90,
            long p99
    ) {
        public static HistogramStats empty() {
            return new HistogramStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
