package com.actionow.ai.controller;

import com.actionow.ai.monitoring.AlertService;
import com.actionow.ai.monitoring.MetricsCollector;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI监控控制器
 * 提供执行指标、统计数据和健康检查端点
 *
 * @author Actionow
 */
@Tag(name = "AI监控", description = "AI执行监控和统计")
@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class AiMonitoringController {

    private final MetricsCollector metricsCollector;
    private final AlertService alertService;

    /**
     * 获取执行指标概览
     */
    @Operation(summary = "获取执行指标概览")
    @GetMapping("/metrics")
    public Result<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // 计数器
        metrics.put("counters", metricsCollector.getAllCounters());

        // 直方图统计
        Map<String, Object> histograms = new HashMap<>();
        histograms.put("executionDuration", metricsCollector.getHistogramStats("executions.duration"));
        histograms.put("apiDuration", metricsCollector.getHistogramStats("api.duration"));
        metrics.put("histograms", histograms);

        // 今日统计
        metrics.put("todayStats", metricsCollector.getTodayStats());

        return Result.success(metrics);
    }

    /**
     * 获取今日统计
     */
    @Operation(summary = "获取今日统计")
    @GetMapping("/stats/today")
    public Result<Map<String, Object>> getTodayStats() {
        return Result.success(metricsCollector.getTodayStats());
    }

    /**
     * 获取提供商统计
     */
    @Operation(summary = "获取提供商统计")
    @GetMapping("/stats/provider/{providerId}")
    public Result<Map<String, Object>> getProviderStats(@PathVariable String providerId) {
        return Result.success(metricsCollector.getProviderStats(providerId));
    }

    /**
     * 获取执行耗时统计
     */
    @Operation(summary = "获取执行耗时统计")
    @GetMapping("/stats/duration")
    public Result<MetricsCollector.HistogramStats> getDurationStats() {
        return Result.success(metricsCollector.getHistogramStats("executions.duration"));
    }

    /**
     * 获取提供商执行耗时统计
     */
    @Operation(summary = "获取提供商执行耗时统计")
    @GetMapping("/stats/duration/provider/{providerId}")
    public Result<MetricsCollector.HistogramStats> getProviderDurationStats(@PathVariable String providerId) {
        return Result.success(metricsCollector.getHistogramStats("executions.duration.provider." + providerId));
    }

    /**
     * 获取成功率
     */
    @Operation(summary = "获取成功率")
    @GetMapping("/stats/success-rate")
    public Result<Map<String, Object>> getSuccessRate() {
        long success = metricsCollector.getCounter("executions.success");
        long failed = metricsCollector.getCounter("executions.failed");
        long total = success + failed;

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("total", total);
        result.put("successRate", total > 0 ? (double) success / total : 0);

        return Result.success(result);
    }

    /**
     * 健康检查
     */
    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        // 检查关键指标
        long started = metricsCollector.getCounter("executions.started");
        long completed = metricsCollector.getCounter("executions.success")
                + metricsCollector.getCounter("executions.failed");

        health.put("executionsStarted", started);
        health.put("executionsCompleted", completed);

        // 计算积压
        long backlog = started - completed;
        health.put("executionBacklog", Math.max(0, backlog));

        if (backlog > 100) {
            health.put("status", "DEGRADED");
            health.put("warning", "执行积压过多");
        }

        return Result.success(health);
    }

    // ==================== 管理端点（需要系统权限） ====================

    /**
     * 手动触发告警（测试用）
     */
    @Operation(summary = "手动触发告警（测试用）")
    @PostMapping("/alert/test")
    @RequireSystemTenant
    public Result<Void> triggerTestAlert(@RequestParam String message) {
        alertService.triggerAlert(
                AlertService.AlertType.SYSTEM_ERROR,
                AlertService.AlertLevel.WARNING,
                "测试告警: " + message,
                Map.of("source", "manual", "timestamp", System.currentTimeMillis())
        );
        return Result.success();
    }

    /**
     * 重置内存统计
     */
    @Operation(summary = "重置内存统计")
    @PostMapping("/metrics/reset")
    @RequireSystemTenant
    public Result<Void> resetMetrics() {
        metricsCollector.resetInMemoryStats();
        return Result.success();
    }

    /**
     * 清理告警冷却
     */
    @Operation(summary = "清理告警冷却")
    @PostMapping("/alert/cleanup-cooldowns")
    @RequireSystemTenant
    public Result<Void> cleanupAlertCooldowns() {
        alertService.cleanupCooldowns();
        return Result.success();
    }
}
