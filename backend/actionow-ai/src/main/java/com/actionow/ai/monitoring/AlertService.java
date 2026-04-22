package com.actionow.ai.monitoring;

import com.actionow.ai.config.AiRuntimeConfigService;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.producer.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警服务
 * 监控异常情况并发送告警通知
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final MessageProducer messageProducer;
    private final MetricsCollector metricsCollector;
    private final AiRuntimeConfigService aiRuntimeConfig;

    // 连续失败计数器（按提供商）
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    // 告警冷却（防止重复告警）
    private final Map<String, LocalDateTime> alertCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MINUTES = 10;

    /**
     * 告警级别
     */
    public enum AlertLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    /**
     * 告警类型
     */
    public enum AlertType {
        HIGH_ERROR_RATE,
        SLOW_RESPONSE,
        CONSECUTIVE_FAILURES,
        PROVIDER_UNAVAILABLE,
        QUOTA_EXHAUSTED,
        SCRIPT_ERROR,
        SYSTEM_ERROR
    }

    /**
     * 检查执行结果并触发告警
     */
    @Async
    public void checkAndAlert(ExecutionMetrics metrics) {
        if (!aiRuntimeConfig.isAlertEnabled()) {
            return;
        }

        try {
            // 检查执行失败
            if (metrics.isFailed()) {
                handleExecutionFailure(metrics);
            }

            // 检查响应时间
            if (metrics.getTotalDurationMs() != null && metrics.getTotalDurationMs() > aiRuntimeConfig.getAlertResponseTimeThresholdMs()) {
                triggerAlert(AlertType.SLOW_RESPONSE, AlertLevel.WARNING,
                        "执行响应时间过长",
                        Map.of(
                                "executionId", metrics.getExecutionId(),
                                "providerId", metrics.getProviderId(),
                                "durationMs", metrics.getTotalDurationMs(),
                                "threshold", aiRuntimeConfig.getAlertResponseTimeThresholdMs()
                        ));
            }

            // 检查错误率
            checkErrorRate(metrics.getProviderId());

        } catch (Exception e) {
            log.error("告警检查失败: executionId={}", metrics.getExecutionId(), e);
        }
    }

    /**
     * 处理执行失败
     */
    private void handleExecutionFailure(ExecutionMetrics metrics) {
        String providerId = metrics.getProviderId();

        // 增加连续失败计数
        int failures = consecutiveFailures
                .computeIfAbsent(providerId, k -> new AtomicInteger(0))
                .incrementAndGet();

        // 检查是否达到连续失败阈值
        if (failures >= aiRuntimeConfig.getAlertConsecutiveFailuresThreshold()) {
            triggerAlert(AlertType.CONSECUTIVE_FAILURES, AlertLevel.ERROR,
                    "模型提供商连续执行失败",
                    Map.of(
                            "providerId", providerId,
                            "providerName", metrics.getProviderName() != null ? metrics.getProviderName() : "",
                            "consecutiveFailures", failures,
                            "lastError", metrics.getErrorMessage() != null ? metrics.getErrorMessage() : "",
                            "errorCode", metrics.getErrorCode() != null ? metrics.getErrorCode() : ""
                    ));
        }

        // 记录脚本错误
        if ("SCRIPT_ERROR".equals(metrics.getErrorCode())) {
            triggerAlert(AlertType.SCRIPT_ERROR, AlertLevel.WARNING,
                    "Groovy脚本执行错误",
                    Map.of(
                            "executionId", metrics.getExecutionId(),
                            "providerId", providerId,
                            "error", metrics.getErrorMessage() != null ? metrics.getErrorMessage() : ""
                    ));
        }
    }

    /**
     * 记录执行成功（重置连续失败计数）
     */
    public void recordSuccess(String providerId) {
        AtomicInteger counter = consecutiveFailures.get(providerId);
        if (counter != null) {
            counter.set(0);
        }
    }

    /**
     * 检查错误率
     */
    private void checkErrorRate(String providerId) {
        Map<String, Object> stats = metricsCollector.getProviderStats(providerId);
        if (stats.isEmpty()) {
            return;
        }

        Object totalObj = stats.get("total");
        Object failedObj = stats.get("failed");

        if (totalObj instanceof Number total && failedObj instanceof Number failed) {
            long totalCount = total.longValue();
            long failedCount = failed.longValue();

            if (totalCount > 10) { // 至少10次执行才计算错误率
                double errorRate = (double) failedCount / totalCount;
                if (errorRate > aiRuntimeConfig.getAlertErrorRateThreshold()) {
                    triggerAlert(AlertType.HIGH_ERROR_RATE, AlertLevel.WARNING,
                            "模型提供商错误率过高",
                            Map.of(
                                    "providerId", providerId,
                                    "errorRate", String.format("%.2f%%", errorRate * 100),
                                    "total", totalCount,
                                    "failed", failedCount,
                                    "threshold", String.format("%.2f%%", aiRuntimeConfig.getAlertErrorRateThreshold() * 100)
                            ));
                }
            }
        }
    }

    /**
     * 手动触发告警
     */
    public void triggerAlert(AlertType type, AlertLevel level, String message, Map<String, Object> details) {
        String alertKey = type.name() + ":" + details.getOrDefault("providerId", "system");

        // 检查冷却
        if (isInCooldown(alertKey)) {
            log.debug("告警处于冷却期，跳过: type={}, key={}", type, alertKey);
            return;
        }

        // 设置冷却
        alertCooldowns.put(alertKey, LocalDateTime.now());

        // 构建告警消息
        Map<String, Object> alertData = new HashMap<>();
        alertData.put("type", type.name());
        alertData.put("level", level.name());
        alertData.put("message", message);
        alertData.put("details", details);
        alertData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        alertData.put("service", "actionow-ai");

        log.warn("触发告警: type={}, level={}, message={}, details={}",
                type, level, message, details);

        // 发送告警消息到MQ
        try {
            MessageWrapper<Map<String, Object>> wrapper = MessageWrapper.wrap(
                    "SYSTEM_ALERT",
                    alertData
            );
            messageProducer.send(MqConstants.EXCHANGE_TOPIC, "notify.system.alert", wrapper);
        } catch (Exception e) {
            log.error("发送告警消息失败: type={}", type, e);
        }

        // 记录告警指标
        metricsCollector.incrementCounter("alerts.triggered");
        metricsCollector.incrementCounter("alerts.triggered." + type.name());
        metricsCollector.incrementCounter("alerts.triggered." + level.name());
    }

    /**
     * 触发提供商不可用告警
     */
    public void triggerProviderUnavailable(String providerId, String providerName, String reason) {
        triggerAlert(AlertType.PROVIDER_UNAVAILABLE, AlertLevel.CRITICAL,
                "模型提供商不可用",
                Map.of(
                        "providerId", providerId,
                        "providerName", providerName != null ? providerName : "",
                        "reason", reason != null ? reason : "未知原因"
                ));
    }

    /**
     * 触发配额耗尽告警
     */
    public void triggerQuotaExhausted(String workspaceId, String userId) {
        triggerAlert(AlertType.QUOTA_EXHAUSTED, AlertLevel.WARNING,
                "用户积分不足",
                Map.of(
                        "workspaceId", workspaceId,
                        "userId", userId
                ));
    }

    /**
     * 检查是否在冷却期
     */
    private boolean isInCooldown(String alertKey) {
        LocalDateTime lastAlert = alertCooldowns.get(alertKey);
        if (lastAlert == null) {
            return false;
        }
        return lastAlert.plusMinutes(COOLDOWN_MINUTES).isAfter(LocalDateTime.now());
    }

    /**
     * 清理过期的冷却记录（每10分钟自动执行）
     */
    @Scheduled(fixedDelay = 600_000)
    public void cleanupCooldowns() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(COOLDOWN_MINUTES * 2);
        alertCooldowns.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }
}
