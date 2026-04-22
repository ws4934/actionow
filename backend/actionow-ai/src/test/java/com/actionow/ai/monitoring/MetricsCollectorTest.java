package com.actionow.ai.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsCollector 单元测试
 * 测试基数限制和 phase 白名单
 */
class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        // MetricsCollector 依赖 RedisTemplate 和 ObjectMapper，
        // 但我们测试的内存指标方法不需要 Redis，传 null 即可
        collector = new MetricsCollector(null, null);
    }

    @Nested
    @DisplayName("计数器基本功能")
    class CounterBasicTests {

        @Test
        @DisplayName("增加计数器并读取")
        void shouldIncrementAndRead() {
            collector.incrementCounter("test.counter");
            assertEquals(1, collector.getCounter("test.counter"));

            collector.incrementCounter("test.counter", 5);
            assertEquals(6, collector.getCounter("test.counter"));
        }

        @Test
        @DisplayName("不存在的计数器返回 0")
        void shouldReturnZeroForMissing() {
            assertEquals(0, collector.getCounter("nonexistent"));
        }
    }

    @Nested
    @DisplayName("计数器基数限制")
    class CounterCardinalityTests {

        @Test
        @DisplayName("超过 MAX_METRIC_KEYS 后新 key 归入 _overflow")
        void shouldOverflowWhenExceedingLimit() {
            // 填满 200 个 key
            for (int i = 0; i < 200; i++) {
                collector.incrementCounter("key." + i);
            }

            // 第 201 个 key 应归入 _overflow
            collector.incrementCounter("key.overflow.test", 42);
            assertEquals(0, collector.getCounter("key.overflow.test"));
            assertEquals(42, collector.getCounter("_overflow"));
        }

        @Test
        @DisplayName("已存在的 key 不受限制")
        void existingKeysShouldStillWork() {
            // 填满
            for (int i = 0; i < 200; i++) {
                collector.incrementCounter("key." + i);
            }
            // 已存在的 key 仍可更新
            collector.incrementCounter("key.0", 10);
            assertEquals(11, collector.getCounter("key.0")); // 1 + 10
        }
    }

    @Nested
    @DisplayName("直方图基数限制")
    class HistogramCardinalityTests {

        @Test
        @DisplayName("超过 MAX_METRIC_KEYS 后拒绝新直方图 key")
        void shouldRejectNewHistogramKeyWhenFull() {
            for (int i = 0; i < 200; i++) {
                collector.recordHistogram("hist." + i, 100);
            }

            // 第 201 个不会创建
            collector.recordHistogram("hist.overflow", 999);
            assertNull(collector.getHistogramStats("hist.overflow").count() == 0 ? null : "should be null");
        }

        @Test
        @DisplayName("正常记录直方图统计")
        void shouldRecordHistogramStats() {
            collector.recordHistogram("test.hist", 100);
            collector.recordHistogram("test.hist", 200);
            collector.recordHistogram("test.hist", 300);

            MetricsCollector.HistogramStats stats = collector.getHistogramStats("test.hist");
            assertEquals(3, stats.count());
            assertEquals(600, stats.sum());
            assertEquals(100, stats.min());
            assertEquals(300, stats.max());
        }
    }

    @Nested
    @DisplayName("脚本阶段白名单")
    class ScriptPhaseTests {

        @Test
        @DisplayName("合法阶段名正常记录")
        void shouldRecordValidPhase() {
            collector.recordScriptPhase("exec-1", "REQUEST_BUILDER", 100);
            MetricsCollector.HistogramStats stats = collector.getHistogramStats("script.phase.REQUEST_BUILDER");
            assertEquals(1, stats.count());
        }

        @Test
        @DisplayName("非法阶段名归入 UNKNOWN")
        void shouldFallbackToUnknownForInvalidPhase() {
            collector.recordScriptPhase("exec-1", "MALICIOUS_PHASE_NAME", 100);
            // 不存在 MALICIOUS_PHASE_NAME 的 key
            assertEquals(0, collector.getHistogramStats("script.phase.MALICIOUS_PHASE_NAME").count());
            // 归入 UNKNOWN
            assertEquals(1, collector.getHistogramStats("script.phase.UNKNOWN").count());
        }
    }

    @Nested
    @DisplayName("Gauge 操作")
    class GaugeTests {

        @Test
        @DisplayName("设置和读取 gauge")
        void shouldSetAndGetGauge() {
            collector.setGauge("active.tasks", 42);
            assertEquals(42, collector.getGauge("active.tasks"));

            collector.setGauge("active.tasks", 0);
            assertEquals(0, collector.getGauge("active.tasks"));
        }
    }

    @Nested
    @DisplayName("重置功能")
    class ResetTests {

        @Test
        @DisplayName("重置后所有指标归零")
        void shouldClearAllStats() {
            collector.incrementCounter("test");
            collector.setGauge("test.gauge", 99);
            collector.recordHistogram("test.hist", 100);

            collector.resetInMemoryStats();

            assertEquals(0, collector.getCounter("test"));
            assertEquals(0, collector.getGauge("test.gauge"));
            assertEquals(0, collector.getHistogramStats("test.hist").count());
        }
    }
}
