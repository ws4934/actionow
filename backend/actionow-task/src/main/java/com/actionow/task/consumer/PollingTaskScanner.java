package com.actionow.task.consumer;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.workspace.WorkspaceInternalClient;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.dto.ExecutionStatusResponse;
import com.actionow.task.dto.ProviderExecutionResult;
import com.actionow.task.entity.Task;
import com.actionow.task.feign.AiFeignClient;
import com.actionow.task.mapper.TaskMapper;
import com.actionow.task.service.AiGenerationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * POLLING / CALLBACK 超时扫描器（含 PENDING 卡住任务兜底）
 * <p>
 * 职责一：POLLING 模式断链恢复
 *   AI 模块以异步方式提交第三方任务后返回 PENDING 状态，Task 模块将任务维持在 RUNNING 状态等待结果。
 *   本扫描器周期性查询 AI 模块状态，检测到终态后驱动完成流程。
 *   <b>持久化保障：</b>扫描基于数据库（t_task）而非内存，AI 服务重启不会导致状态丢失。
 *   NOT_FOUND 状态（AI 重启后内存中 PollingManager 状态消失）已在 handleNotFound() 中处理。
 * <p>
 * 职责二：CALLBACK 模式超时兜底
 *   第三方 AI 服务宕机或网络故障导致回调永远不到达时，60 秒后强制将任务标记为超时失败。
 * <p>
 * 职责三：PENDING 卡住任务积分解冻
 *   MQ 消息丢失（Broker 宕机/路由失败）时，任务停留在 PENDING 状态，积分已冻结但永远不会释放。
 *   每 2 分钟扫描并强制失败超时的 PENDING 任务，触发积分解冻流程。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "actionow.task.compensation.enabled", havingValue = "true", matchIfMissing = true)
public class PollingTaskScanner {

    private static final int POLLING_IDLE_THRESHOLD = 4;

    private final TaskMapper taskMapper;
    private final AiFeignClient aiFeignClient;
    private final AiGenerationFacade aiGenerationFacade;
    private final WorkspaceInternalClient workspaceInternalClient;
    private final TaskRuntimeConfigService runtimeConfig;
    private volatile int pollingIdleCount = 0;

    /**
     * 定时扫描 POLLING 模式任务
     * 默认每 15 秒执行一次，可通过 Redis 动态调整
     */
    @Scheduled(fixedDelayString = "${actionow.task.polling.scan-interval-ms:15000}")
    public void scanPollingTasks() {
        if (pollingIdleCount > POLLING_IDLE_THRESHOLD
                && pollingIdleCount % POLLING_IDLE_THRESHOLD != 0) {
            pollingIdleCount++;
            return;
        }

        int batchSize = runtimeConfig.getPollingScanBatchSize();
        List<Task> tasks = taskMapper.selectPollingRunningTasks(batchSize);

        if (tasks.isEmpty()) {
            pollingIdleCount++;
            if (pollingIdleCount == POLLING_IDLE_THRESHOLD) {
                log.debug("[PollingScanner] 进入空闲降频模式（无 POLLING 任务）");
            }
            log.trace("[PollingScanner] 无 POLLING 模式等待中的任务");
            return;
        }

        pollingIdleCount = 0;
        log.info("[PollingScanner] 发现 {} 个 POLLING 模式运行中任务，开始轮询", tasks.size());

        for (Task task : tasks) {
            try {
                processOne(task);
            } catch (Exception e) {
                log.error("[PollingScanner] 处理任务异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 定时扫描 CALLBACK 模式超时任务
     * <p>
     * 第三方未回调（宕机/网络故障）时，任务将永久卡在 RUNNING 状态。
     * 本扫描器每 60 秒检查一次 timeout_at 已过期的 CALLBACK 任务，强制标记为超时失败。
     * 间隔设为 60 秒（CALLBACK 超时通常 >= 5 分钟，无需高频扫描）。
     */
    @Scheduled(fixedDelayString = "${actionow.task.polling.callback-timeout-scan-interval-ms:60000}")
    public void scanCallbackTimeoutTasks() {
        int batchSize = runtimeConfig.getPollingScanBatchSize();
        List<Task> tasks = taskMapper.selectCallbackTimeoutTasks(batchSize);

        if (tasks.isEmpty()) {
            log.trace("[PollingScanner] 无 CALLBACK 模式超时任务");
            return;
        }

        log.info("[PollingScanner] 发现 {} 个 CALLBACK 模式超时任务，开始处理", tasks.size());

        for (Task task : tasks) {
            try {
                handleCallbackTimeout(task);
            } catch (Exception e) {
                log.error("[PollingScanner] 处理 CALLBACK 超时任务异常: taskId={}, error={}",
                        task.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 定时扫描卡在 PENDING 状态的超时任务
     * <p>
     * 处理 MQ 消息丢失场景：积分已冻结、Task 已创建，但 MQ 消息未被消费（Broker 宕机/路由失败），
     * 导致任务永久挂起，积分被冻结但永远不会释放。
     * 间隔设为 2 分钟（PENDING 超时通常 >= 5 分钟，无需高频扫描）。
     */
    @Scheduled(fixedDelayString = "${actionow.task.polling.pending-timeout-scan-interval-ms:120000}")
    public void scanStuckPendingTasks() {
        int batchSize = runtimeConfig.getPollingScanBatchSize();
        List<Task> tasks = taskMapper.selectStuckPendingTasks(batchSize);

        if (tasks.isEmpty()) {
            log.trace("[PollingScanner] 无卡住的 PENDING 超时任务");
            return;
        }

        log.warn("[PollingScanner] 发现 {} 个卡在 PENDING 状态的超时任务，开始处理", tasks.size());

        for (Task task : tasks) {
            try {
                handleStuckPendingTask(task);
            } catch (Exception e) {
                log.error("[PollingScanner] 处理卡住 PENDING 任务异常: taskId={}, error={}",
                        task.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 将卡住的 PENDING 任务标记为失败并触发积分解冻
     * <p>
     * 根本原因：MQ 消息丢失（Broker 不可用/路由错误），任务从未被消费。
     * 处理方式：将任务标记为 TIMEOUT，触发完整失败流程（退还积分、更新 Asset 状态等）。
     */
    private void handleStuckPendingTask(Task task) {
        log.warn("[PollingScanner] PENDING 任务超时，MQ 消息可能丢失，标记失败: " +
                "taskId={}, createdAt={}", task.getId(), task.getCreatedAt());
        restoreContext(task);
        try {
            ProviderExecutionResult timeoutResult = ProviderExecutionResult.builder()
                    .success(false)
                    .status("TIMEOUT")
                    .errorCode("PENDING_TIMEOUT")
                    .errorMessage("任务长期卡在 PENDING 状态（MQ 消息可能丢失），已自动超时处理")
                    .build();
            aiGenerationFacade.handleCompletion(task.getId(), timeoutResult);
            log.info("[PollingScanner] PENDING 超时任务已标记失败: taskId={}", task.getId());
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 将超时的 CALLBACK 任务标记为失败
     * <p>
     * 第三方从未回调时兜底：恢复租户上下文后触发完整失败流程（退还积分等）。
     */
    private void handleCallbackTimeout(Task task) {
        log.warn("[PollingScanner] CALLBACK 任务超时，第三方回调未到达，标记失败: " +
                "taskId={}, timeoutAt={}", task.getId(), task.getTimeoutAt());
        restoreContext(task);
        try {
            ProviderExecutionResult timeoutResult = ProviderExecutionResult.builder()
                    .success(false)
                    .status("TIMEOUT")
                    .errorCode("CALLBACK_TIMEOUT")
                    .errorMessage("CALLBACK 任务超时：第三方服务未在规定时间内回调")
                    .build();
            aiGenerationFacade.handleCompletion(task.getId(), timeoutResult);
            log.info("[PollingScanner] CALLBACK 超时任务已标记失败: taskId={}", task.getId());
        } finally {
            UserContextHolder.clear();
        }
    }

    // ==================== 单任务处理 ====================

    private void processOne(Task task) {
        Map<String, Object> inputParams = task.getInputParams();
        if (inputParams == null) {
            log.warn("[PollingScanner] inputParams 为空，跳过: taskId={}", task.getId());
            return;
        }

        String executionId = (String) inputParams.get("executionId");
        if (!StringUtils.hasText(executionId)) {
            log.warn("[PollingScanner] 缺少 executionId，跳过: taskId={}", task.getId());
            return;
        }

        // 非阻塞查询当前执行状态
        Result<ExecutionStatusResponse> statusResult;
        try {
            statusResult = aiFeignClient.getExecutionStatus(executionId);
        } catch (Exception e) {
            log.warn("[PollingScanner] 查询执行状态失败（AI 服务可能不可用）: taskId={}, executionId={}, error={}",
                    task.getId(), executionId, e.getMessage());
            return;
        }

        if (!statusResult.isSuccess() || statusResult.getData() == null) {
            log.warn("[PollingScanner] 查询执行状态返回失败: taskId={}, executionId={}",
                    task.getId(), executionId);
            return;
        }

        ExecutionStatusResponse execStatus = statusResult.getData();
        log.debug("[PollingScanner] 执行状态: taskId={}, executionId={}, status={}, completed={}",
                task.getId(), executionId, execStatus.getStatus(), execStatus.isCompleted());

        if (isTaskTimedOut(task)) {
            handlePollingTimeout(task, executionId, "POLLING 任务超时：执行时间超过限制");
            return;
        }

        if ("NOT_FOUND".equals(execStatus.getStatus())) {
            // AI 模块重启后内存状态丢失，若任务已超时则强制失败
            handleNotFound(task, executionId);
            return;
        }

        if (!execStatus.isCompleted()) {
            // 仍在执行中，等待下次扫描
            return;
        }

        // 状态已终结，获取完整结果（传 timeout=10s 作为安全边界，正常应立即返回）
        Result<ProviderExecutionResult> resultData;
        try {
            resultData = aiFeignClient.getExecutionResult(executionId, 10);
        } catch (Exception e) {
            log.error("[PollingScanner] 获取执行结果 Feign 调用失败: taskId={}, executionId={}, error={}",
                    task.getId(), executionId, e.getMessage(), e);
            return;
        }

        if (!resultData.isSuccess() || resultData.getData() == null) {
            log.error("[PollingScanner] 获取执行结果失败（AI 返回失败或超时）: taskId={}, executionId={}，msg={}",
                    task.getId(), executionId, resultData.getMessage());
            return;
        }

        // 恢复租户上下文，触发完整完成流程
        restoreContext(task);
        try {
            aiGenerationFacade.handleCompletion(task.getId(), resultData.getData());
            log.info("[PollingScanner] POLLING 任务处理完成: taskId={}, aiStatus={}",
                    task.getId(), execStatus.getStatus());
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * AI 模块内不存在该执行记录时的处理
     * <p>
     * 通常是 AI 模块重启后内存 PollingManager 状态丢失。
     * 若任务已超过 timeout_at，直接标记为超时失败；否则等待后续扫描。
     */
    private void handleNotFound(Task task, String executionId) {
        if (isTaskTimedOut(task)) {
            handlePollingTimeout(task, executionId, "POLLING 任务超时：AI 执行记录不存在（AI 服务可能已重启）");
        } else {
            log.warn("[PollingScanner] AI 执行记录不存在（AI 可能已重启），等待后续扫描: " +
                    "taskId={}, executionId={}", task.getId(), executionId);
        }
    }

    private void handlePollingTimeout(Task task, String executionId, String errorMessage) {
        log.warn("[PollingScanner] POLLING 任务超时，标记失败: taskId={}, executionId={}, deadline={}",
                task.getId(), executionId, resolveTimeoutAt(task));
        restoreContext(task);
        try {
            ProviderExecutionResult timeoutResult = ProviderExecutionResult.builder()
                    .success(false)
                    .status("TIMEOUT")
                    .errorCode("POLLING_TIMEOUT")
                    .errorMessage(errorMessage)
                    .build();
            aiGenerationFacade.handleCompletion(task.getId(), timeoutResult);
        } finally {
            UserContextHolder.clear();
        }
    }

    private boolean isTaskTimedOut(Task task) {
        LocalDateTime timeoutAt = resolveTimeoutAt(task);
        return LocalDateTime.now().isAfter(timeoutAt);
    }

    /**
     * 解析任务超时时间，保证永远不返回 null。
     * <p>
     * 优先级: timeout_at > started_at + timeout_seconds > started_at + 默认超时 > created_at + 默认超时
     * 兜底策略：使用 RuntimeConfig 中的默认超时秒数，防止僵尸任务永不超时。
     */
    private LocalDateTime resolveTimeoutAt(Task task) {
        if (task.getTimeoutAt() != null) {
            return task.getTimeoutAt();
        }

        int defaultTimeout = runtimeConfig.getDefaultTimeoutSeconds();

        if (task.getStartedAt() != null) {
            int timeoutSeconds = (task.getTimeoutSeconds() != null && task.getTimeoutSeconds() > 0)
                    ? task.getTimeoutSeconds() : defaultTimeout;
            return task.getStartedAt().plusSeconds(timeoutSeconds);
        }

        // 兜底：如果 started_at 也为 null（不应该发生），使用 created_at
        LocalDateTime baseTime = task.getCreatedAt() != null ? task.getCreatedAt() : LocalDateTime.now();
        log.warn("[PollingScanner] 任务缺少 started_at，使用 created_at 计算超时: taskId={}", task.getId());
        return baseTime.plusSeconds(defaultTimeout);
    }

    /**
     * 从 Task 实体恢复租户上下文
     * 供后续调用 AssetFeignClient、WalletFeignClient 等需要租户 Schema 的 Feign 调用使用
     */
    private void restoreContext(Task task) {
        UserContext context = new UserContext();
        context.setWorkspaceId(task.getWorkspaceId());
        context.setUserId(task.getCreatorId());

        if (StringUtils.hasText(task.getWorkspaceId())) {
            try {
                Result<String> schemaResult = workspaceInternalClient.getTenantSchema(task.getWorkspaceId());
                if (schemaResult.isSuccess() && StringUtils.hasText(schemaResult.getData())) {
                    context.setTenantSchema(schemaResult.getData());
                } else {
                    log.warn("[PollingScanner] 获取租户 Schema 失败: workspaceId={}", task.getWorkspaceId());
                }
            } catch (Exception e) {
                log.error("[PollingScanner] 获取租户 Schema 异常: workspaceId={}, error={}",
                        task.getWorkspaceId(), e.getMessage());
            }
        }

        UserContextHolder.setContext(context);
    }
}
