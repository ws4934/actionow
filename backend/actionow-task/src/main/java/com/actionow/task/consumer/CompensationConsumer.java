package com.actionow.task.consumer;

import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.producer.MessageProducer;
import com.actionow.common.redis.lock.DistributedLockService;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.entity.CompensationTask;
import com.actionow.task.mapper.CompensationTaskMapper;
import com.actionow.task.service.PointsTransactionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 补偿任务消费者
 * 定时扫描待重试的补偿任务并执行
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "actionow.task.compensation.enabled", havingValue = "true", matchIfMissing = true)
public class CompensationConsumer {

    private static final String SCAN_LOCK_KEY = "task:compensation:scan";
    private static final String STATS_LOCK_KEY = "task:compensation:stats";
    private static final String LAST_ALERTED_COUNT_KEY = "task:compensation:lastAlertedExhaustedCount";
    private static final Duration LAST_ALERTED_COUNT_TTL = Duration.ofDays(7);

    private final CompensationTaskMapper compensationTaskMapper;
    private final PointsTransactionManager pointsTransactionManager;
    private final MessageProducer messageProducer;
    private final DistributedLockService distributedLockService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 定时扫描待重试的补偿任务
     * 默认每 30 秒执行一次
     */
    @Scheduled(fixedDelayString = "${actionow.task.compensation.scan-interval-ms:30000}")
    public void scanAndRetry() {
        // 分布式锁：多实例部署时只有一个实例执行扫描，其余跳过
        distributedLockService.executeWithLock(SCAN_LOCK_KEY, 0, 60, TimeUnit.SECONDS, () -> {
            List<CompensationTask> tasks = compensationTaskMapper.selectPendingRetryTasks(
                    LocalDateTime.now(),
                    TaskConstants.Compensation.BATCH_SIZE);

            if (tasks.isEmpty()) {
                log.trace("没有待重试的补偿任务");
                return;
            }

            log.info("发现 {} 个待重试的补偿任务", tasks.size());

            for (CompensationTask task : tasks) {
                try {
                    pointsTransactionManager.retryCompensation(task.getId());
                } catch (Exception e) {
                    log.error("处理补偿任务异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 统计补偿任务状态
     * 每 5 分钟输出一次统计信息，EXHAUSTED 状态触发告警
     */
    @Scheduled(fixedRate = 300000)
    public void logStats() {
        // 分布式锁：统计和告警只由一个实例执行
        distributedLockService.executeWithLock(STATS_LOCK_KEY, 0, 30, TimeUnit.SECONDS, () -> {
            int pendingCount = compensationTaskMapper.countByStatus(null, TaskConstants.CompensationStatus.PENDING);
            int processingCount = compensationTaskMapper.countByStatus(null, TaskConstants.CompensationStatus.PROCESSING);
            int exhaustedCount = compensationTaskMapper.countByStatus(null, TaskConstants.CompensationStatus.EXHAUSTED);

            if (pendingCount > 0 || processingCount > 0 || exhaustedCount > 0) {
                log.info("补偿任务统计: pending={}, processing={}, exhausted={}",
                        pendingCount, processingCount, exhaustedCount);
            }

            // 耗尽的任务触发系统告警（仅在数量增加时发送，避免告警风暴）
            if (exhaustedCount > 0) {
                log.error("存在 {} 个已耗尽重试的补偿任务，需要人工处理！", exhaustedCount);
                int lastAlerted = readLastAlertedCount();
                if (exhaustedCount > lastAlerted) {
                    sendCompensationExhaustedAlert(exhaustedCount);
                    writeLastAlertedCount(exhaustedCount);
                }
            } else {
                // exhausted 全部清零后重置，下次再出现新 exhausted 时重新告警
                deleteLastAlertedCount();
            }
        });
    }

    private int readLastAlertedCount() {
        try {
            String value = stringRedisTemplate.opsForValue().get(LAST_ALERTED_COUNT_KEY);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("读取告警去重计数器失败（默认为0，可能触发重复告警）: {}", e.getMessage());
            return 0;
        }
    }

    private void writeLastAlertedCount(int count) {
        try {
            stringRedisTemplate.opsForValue().set(
                    LAST_ALERTED_COUNT_KEY, String.valueOf(count), LAST_ALERTED_COUNT_TTL);
        } catch (Exception e) {
            log.warn("写入告警去重计数器失败（下次可能重复告警）: {}", e.getMessage());
        }
    }

    private void deleteLastAlertedCount() {
        try {
            stringRedisTemplate.delete(LAST_ALERTED_COUNT_KEY);
        } catch (Exception e) {
            log.warn("清除告警去重计数器失败: {}", e.getMessage());
        }
    }

    /**
     * 发送补偿任务耗尽告警
     * 通过 MQ 系统告警通道通知运维人员
     */
    private void sendCompensationExhaustedAlert(int exhaustedCount) {
        try {
            Map<String, Object> alertPayload = new HashMap<>();
            alertPayload.put("alertType", "COMPENSATION_EXHAUSTED");
            alertPayload.put("alertLevel", "CRITICAL");
            alertPayload.put("source", "actionow-task");
            alertPayload.put("message", String.format(
                    "补偿任务重试耗尽: %d 个任务无法自动恢复，可能存在积分冻结未释放。请立即排查 t_compensation_task 表中 status='EXHAUSTED' 的记录。",
                    exhaustedCount));
            alertPayload.put("exhaustedCount", exhaustedCount);
            alertPayload.put("timestamp", LocalDateTime.now().toString());

            MessageWrapper<Map<String, Object>> message = MessageWrapper.wrap(
                    MqConstants.Alert.MSG_TYPE, alertPayload);
            messageProducer.send(MqConstants.EXCHANGE_DIRECT, MqConstants.Alert.ROUTING, message);

            log.info("已发送补偿任务耗尽告警: exhaustedCount={}", exhaustedCount);
        } catch (Exception e) {
            log.error("发送补偿任务耗尽告警失败", e);
        }
    }
}
