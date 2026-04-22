package com.actionow.task.service.impl;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.producer.MessageProducer;
import com.actionow.common.redis.lock.DistributedLockService;
import com.actionow.common.security.workspace.WorkspaceInternalClient;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.CompensationType;
import com.actionow.task.dto.ConfirmConsumeRequest;
import com.actionow.task.dto.FreezeRequest;
import com.actionow.task.dto.FreezeResponse;
import com.actionow.task.dto.UnfreezeRequest;
import com.actionow.task.entity.CompensationTask;
import com.actionow.task.feign.WalletFeignClient;
import com.actionow.task.mapper.CompensationTaskMapper;
import com.actionow.task.service.CompensationTaskService;
import com.actionow.task.service.PointsTransactionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 积分事务管理器实现
 * 提供冻结、解冻、确认消费操作，并自动创建补偿任务处理失败情况
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsTransactionManagerImpl implements PointsTransactionManager {

    private static final String FREEZE_LOCK_PREFIX = "task:freeze:lock:";
    private static final String FREEZE_IDEMPOTENT_PREFIX = "task:freeze:done:";
    private static final Duration FREEZE_IDEMPOTENT_TTL = Duration.ofMinutes(10);
    /**
     * 钱包侧"冻结记录不存在"错误码。
     * 当 unfreeze/confirmConsume 第二次调用时，冻结记录已被第一次调用消耗，
     * 钱包返回此错误。对 task 侧而言应视为幂等成功（第一次已生效），
     * 继续抛异常会导致补偿任务无限重试，最终误触发耗尽告警。
     */
    private static final String WALLET_FROZEN_RECORD_NOT_FOUND = "40004";

    private final WalletFeignClient walletFeignClient;
    private final CompensationTaskMapper compensationTaskMapper;
    private final WorkspaceInternalClient workspaceInternalClient;
    private final MessageProducer messageProducer;
    private final DistributedLockService distributedLockService;
    private final StringRedisTemplate stringRedisTemplate;
    // 独立 Bean：使补偿任务创建在 REQUIRES_NEW 事务中独立提交，避免同类自调用绕过 AOP 代理
    private final CompensationTaskService compensationTaskService;
    private final TaskRuntimeConfigService runtimeConfig;

    @Override
    public String freezePoints(String workspaceId, String userId, Long amount,
                               String businessType, String businessId, String remark) {
        log.info("冻结积分: workspaceId={}, userId={}, amount={}, businessId={}",
                workspaceId, userId, amount, businessId);

        // 幂等保护：如果相同 businessId 最近已成功冻结，直接返回 transactionId
        String idempotentKey = FREEZE_IDEMPOTENT_PREFIX + businessId;
        String cached = null;
        try {
            cached = stringRedisTemplate.opsForValue().get(idempotentKey);
        } catch (Exception e) {
            log.warn("幂等缓存读取失败（降级为穿透）: businessId={}, error={}", businessId, e.getMessage());
        }
        if (cached != null) {
            log.info("幂等命中，跳过重复冻结: businessId={}, transactionId={}", businessId, cached);
            return cached;
        }

        // 分布式锁：同一 businessId 串行化，防止并发/Feign 重试导致重复冻结
        String lockKey = FREEZE_LOCK_PREFIX + businessId;
        String transactionId = distributedLockService.executeWithLock(
                lockKey, 5, 10, TimeUnit.SECONDS, () -> {
                    // 锁内再次检查幂等（另一线程可能刚完成）
                    try {
                        String rechecked = stringRedisTemplate.opsForValue().get(idempotentKey);
                        if (rechecked != null) {
                            return rechecked;
                        }
                    } catch (Exception e) {
                        log.warn("幂等缓存锁内复查失败（降级为执行冻结）: businessId={}, error={}", businessId, e.getMessage());
                    }
                    return doFreezePoints(workspaceId, userId, amount, businessType, businessId, remark);
                });

        if (transactionId == null) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION.getCode(),
                    "积分冻结并发冲突，请稍后重试: businessId=" + businessId);
        }

        return transactionId;
    }

    /**
     * 实际冻结积分流程（在分布式锁保护下执行）
     */
    private String doFreezePoints(String workspaceId, String userId, Long amount,
                                  String businessType, String businessId, String remark) {
        // Step 1: 先占用配额（wallet 侧原子操作：分布式锁 + 乐观锁 + 版本校验）。
        // 旧流程是 check→freeze→use 三步非原子（TOCTOU 漏洞），
        // 现在直接 use（内部包含 check），失败说明配额真正不足。
        if (!useMemberQuota(workspaceId, userId, amount)) {
            log.warn("成员配额不足: workspaceId={}, userId={}, amount={}", workspaceId, userId, amount);
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED.getCode(), "成员配额不足，请联系管理员增加配额");
        }

        // Step 2: 冻结积分
        FreezeRequest request = FreezeRequest.builder()
                .workspaceId(workspaceId)
                .operatorId(userId)
                .amount(amount)
                .businessType(businessType)
                .businessId(businessId)
                .remark(remark)
                .build();

        Result<FreezeResponse> result;
        try {
            result = walletFeignClient.freeze(workspaceId, request);
        } catch (Exception e) {
            log.error("冻结积分网络异常，回滚配额: workspaceId={}, businessId={}, error={}",
                    workspaceId, businessId, e.getMessage());
            rollbackQuotaSafe(workspaceId, userId, amount, businessId, businessType);
            throw new BusinessException(ResultCode.FAIL.getCode(), "积分冻结失败: " + e.getMessage());
        }

        if (!result.isSuccess()) {
            log.error("冻结积分失败，回滚配额: workspaceId={}, businessId={}, error={}",
                    workspaceId, businessId, result.getMessage());
            rollbackQuotaSafe(workspaceId, userId, amount, businessId, businessType);
            throw new BusinessException(ResultCode.FAIL.getCode(), "积分冻结失败: " + result.getMessage());
        }

        String transactionId = result.getData() != null ? result.getData().getTransactionId() : null;
        if (transactionId == null) {
            // 钱包冻结成功但未返回 transactionId，用 businessId 兜底（避免 null 被误判为锁获取失败）
            log.warn("钱包冻结成功但 transactionId 为 null，使用 businessId 兜底: businessId={}", businessId);
            transactionId = businessId;
        }

        // 记录幂等 token：短 TTL 内重复调用返回相同 transactionId
        try {
            stringRedisTemplate.opsForValue().set(
                    FREEZE_IDEMPOTENT_PREFIX + businessId, transactionId, FREEZE_IDEMPOTENT_TTL);
        } catch (Exception e) {
            log.warn("幂等 token 写入失败（非致命，下次调用将穿透到 wallet）: businessId={}, error={}",
                    businessId, e.getMessage());
        }

        log.info("冻结积分成功: workspaceId={}, businessId={}, transactionId={}",
                workspaceId, businessId, transactionId);
        return transactionId;
    }

    /**
     * 安全退还配额（best-effort）。失败时创建补偿任务兜底。
     */
    private void rollbackQuotaSafe(String workspaceId, String userId, Long amount,
                                   String businessId, String businessType) {
        if (!refundMemberQuota(workspaceId, userId, amount)) {
            log.error("配额退还失败，创建补偿任务: workspaceId={}, userId={}, amount={}",
                    workspaceId, userId, amount);
            compensationTaskService.createCompensationTask(CompensationType.REFUND_QUOTA, workspaceId, userId,
                    businessId, businessType, amount, "冻结失败后配额退还失败", "refundMemberQuota 返回失败");
        }
    }

    @Override
    @Async
    public void unfreezePointsAsync(String workspaceId, String userId,
                                    String businessId, String businessType, String remark,
                                    Long frozenAmount) {
        log.info("异步解冻积分: workspaceId={}, userId={}, businessId={}, businessType={}, frozenAmount={}",
                workspaceId, userId, businessId, businessType, frozenAmount);

        try {
            doUnfreeze(workspaceId, userId, businessId, businessType, remark, frozenAmount);
            log.info("异步解冻积分成功: businessId={}", businessId);
        } catch (Exception e) {
            log.error("异步解冻积分失败，创建补偿任务: businessId={}, error={}",
                    businessId, e.getMessage());
            compensationTaskService.createCompensationTask(CompensationType.UNFREEZE, workspaceId, userId,
                    businessId, businessType, frozenAmount, remark, e.getMessage());
        }
    }

    @Override
    @Async
    public void confirmConsumeAsync(String workspaceId, String userId,
                                    String businessId, String businessType, Long actualAmount, String remark) {
        log.info("异步确认消费: workspaceId={}, userId={}, businessId={}, businessType={}, amount={}",
                workspaceId, userId, businessId, businessType, actualAmount);

        try {
            doConfirmConsume(workspaceId, userId, businessId, businessType, actualAmount, remark);
            log.info("异步确认消费成功: businessId={}", businessId);
        } catch (Exception e) {
            log.error("异步确认消费失败，创建补偿任务: businessId={}, error={}",
                    businessId, e.getMessage());
            compensationTaskService.createCompensationTask(CompensationType.CONFIRM_CONSUME, workspaceId, userId,
                    businessId, businessType, actualAmount, remark, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void retryCompensation(String compensationTaskId) {
        CompensationTask task = compensationTaskMapper.selectById(compensationTaskId);
        if (task == null) {
            log.warn("补偿任务不存在: taskId={}", compensationTaskId);
            return;
        }

        // 检查是否超过最大重试次数
        int maxRetry = runtimeConfig.getCompensationMaxRetryCount();
        if (task.getRetryCount() >= maxRetry) {
            log.error("补偿任务重试次数已耗尽: taskId={}, retryCount={}",
                    compensationTaskId, task.getRetryCount());
            compensationTaskMapper.markExhausted(compensationTaskId,
                    "重试次数已耗尽 (max=" + maxRetry + ")");
            return;
        }

        // 尝试锁定任务
        int updated = compensationTaskMapper.tryLockTask(compensationTaskId, task.getVersion());
        if (updated == 0) {
            log.debug("补偿任务已被其他实例处理: taskId={}", compensationTaskId);
            return;
        }

        try {
            // 恢复用户上下文（定时调度线程无上下文，需从补偿任务中恢复）
            restoreContextFromTask(task);
            executeCompensation(task);
            compensationTaskMapper.markCompleted(compensationTaskId);
            log.info("补偿任务执行成功: taskId={}, type={}", compensationTaskId, task.getType());
        } catch (Exception e) {
            log.error("补偿任务执行失败: taskId={}, error={}", compensationTaskId, e.getMessage());
            handleRetryFailure(task, e.getMessage());
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 从补偿任务中恢复用户上下文
     * 定时调度线程无 HTTP 请求上下文，需手动设置以便 FeignRequestInterceptor 正确传递请求头
     * 注意：必须同时设置 tenantSchema，否则 TenantSchemaInterceptor 会拒绝后续 DB 操作
     */
    private void restoreContextFromTask(CompensationTask task) {
        Map<String, Object> payload = task.getPayload();
        String userId = (String) payload.get("userId");

        UserContext context = new UserContext();
        context.setWorkspaceId(task.getWorkspaceId());
        context.setUserId(userId);

        if (task.getWorkspaceId() != null) {
            try {
                Result<String> schemaResult = workspaceInternalClient.getTenantSchema(task.getWorkspaceId());
                if (schemaResult.isSuccess() && schemaResult.getData() != null) {
                    context.setTenantSchema(schemaResult.getData());
                }
            } catch (Exception e) {
                log.warn("获取租户Schema失败: workspaceId={}", task.getWorkspaceId(), e);
            }
        }

        UserContextHolder.setContext(context);
    }

    /**
     * 执行补偿操作
     */
    private void executeCompensation(CompensationTask task) {
        Map<String, Object> payload = task.getPayload();
        String userId = (String) payload.get("userId");
        String businessId = (String) payload.get("businessId");
        String businessType = (String) payload.get("businessType");
        String remark = (String) payload.get("remark");

        CompensationType type = CompensationType.fromCode(task.getType());
        switch (type) {
            case UNFREEZE:
                Long unfreezeAmount = payload.get("amount") != null ?
                        ((Number) payload.get("amount")).longValue() : null;
                doUnfreeze(task.getWorkspaceId(), userId, businessId, businessType, remark, unfreezeAmount);
                break;
            case CONFIRM_CONSUME:
                Long amount = payload.get("amount") != null ?
                        ((Number) payload.get("amount")).longValue() : null;
                doConfirmConsume(task.getWorkspaceId(), userId, businessId, businessType, amount, remark);
                break;
            case REFUND_QUOTA:
                Long quotaAmount = payload.get("amount") != null ?
                        ((Number) payload.get("amount")).longValue() : 0L;
                doRefundQuota(task.getWorkspaceId(), userId, quotaAmount);
                break;
            default:
                throw new IllegalStateException("未知的补偿类型: " + task.getType());
        }
    }

    /**
     * 执行解冻操作（包含退还配额）
     *
     * @param frozenAmount 原始冻结金额，用于退还成员配额；null 表示无需退还配额（如流式场景未扣配额）
     */
    private void doUnfreeze(String workspaceId, String userId,
                            String businessId, String businessType, String remark,
                            Long frozenAmount) {
        // 1. 解冻积分
        doUnfreezeOnly(workspaceId, userId, businessId, businessType, remark);

        // 2. 退还成员配额（失败不影响解冻，自动创建补偿任务）
        if (frozenAmount != null && frozenAmount > 0) {
            if (!refundMemberQuota(workspaceId, userId, frozenAmount)) {
                log.warn("配额退还失败，创建补偿任务: workspaceId={}, userId={}, amount={}",
                        workspaceId, userId, frozenAmount);
                compensationTaskService.createCompensationTask(CompensationType.REFUND_QUOTA, workspaceId, userId,
                        businessId, businessType, frozenAmount, "解冻后配额退还失败，自动补偿", "refundMemberQuota 返回失败");
            }
        }
    }

    /**
     * 仅执行解冻操作（不退还配额）
     */
    private void doUnfreezeOnly(String workspaceId, String userId,
                                String businessId, String businessType, String remark) {
        UnfreezeRequest request = UnfreezeRequest.builder()
                .workspaceId(workspaceId)
                .operatorId(userId)
                .businessId(businessId)
                .businessType(businessType)
                .remark(remark)
                .build();

        Result<Void> result = walletFeignClient.unfreeze(workspaceId, request);
        if (!result.isSuccess()) {
            // 幂等兼容：冻结记录不存在说明首次解冻已成功，重复调用视为成功返回
            if (WALLET_FROZEN_RECORD_NOT_FOUND.equals(result.getCode())) {
                log.info("解冻幂等命中，冻结记录已消耗: workspaceId={}, businessId={}",
                        workspaceId, businessId);
            } else {
                throw new RuntimeException("解冻失败: " + result.getMessage());
            }
        }

        // 清除冻结幂等 token：解冻后若同一 businessId 再次调用 freezePoints，
        // 必须走真实冻结流程而不是被短 TTL 的缓存误认为幂等命中。
        try {
            stringRedisTemplate.delete(FREEZE_IDEMPOTENT_PREFIX + businessId);
        } catch (Exception e) {
            log.warn("清除冻结幂等 token 失败（非致命，最多 {} 后自动过期）: businessId={}, error={}",
                    FREEZE_IDEMPOTENT_TTL, businessId, e.getMessage());
        }
    }

    /**
     * 执行退还配额操作（用于补偿任务）
     */
    private void doRefundQuota(String workspaceId, String userId, long amount) {
        Result<Boolean> result = walletFeignClient.refundQuota(workspaceId, userId, amount);
        if (!result.isSuccess()) {
            throw new RuntimeException("退还配额失败: " + result.getMessage());
        }
        log.info("补偿任务退还配额成功: workspaceId={}, userId={}, amount={}",
                workspaceId, userId, amount);
    }

    /**
     * 执行确认消费操作
     */
    private void doConfirmConsume(String workspaceId, String userId,
                                  String businessId, String businessType, Long actualAmount, String remark) {
        ConfirmConsumeRequest request = ConfirmConsumeRequest.builder()
                .workspaceId(workspaceId)
                .operatorId(userId)
                .businessId(businessId)
                .businessType(businessType)
                .actualAmount(actualAmount)
                .remark(remark)
                .build();

        Result<Void> result = walletFeignClient.confirmConsume(workspaceId, request);
        if (!result.isSuccess()) {
            // 幂等兼容：冻结记录不存在说明首次确认消费已成功，重复调用视为成功返回
            if (WALLET_FROZEN_RECORD_NOT_FOUND.equals(result.getCode())) {
                log.info("确认消费幂等命中，冻结记录已消耗: workspaceId={}, businessId={}",
                        workspaceId, businessId);
                return;
            }
            throw new RuntimeException("确认消费失败: " + result.getMessage());
        }
    }

    /**
     * 处理重试失败，计算下次重试时间（指数退避）
     */
    private void handleRetryFailure(CompensationTask task, String errorMessage) {
        int nextRetryCount = task.getRetryCount() + 1;
        int maxRetry = runtimeConfig.getCompensationMaxRetryCount();

        if (nextRetryCount >= maxRetry) {
            compensationTaskMapper.markExhausted(task.getId(), errorMessage);
            log.error("补偿任务重试次数已耗尽: taskId={}, type={}", task.getId(), task.getType());
            sendCompensationExhaustedAlert(task, errorMessage);
            return;
        }

        // 指数退避：30s, 60s, 120s, 240s, 480s
        int delaySeconds = runtimeConfig.getCompensationInitialRetryDelaySeconds() *
                (int) Math.pow(2, nextRetryCount - 1);
        // 限制最大延迟
        delaySeconds = Math.min(delaySeconds, runtimeConfig.getCompensationMaxRetryDelaySeconds());

        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        compensationTaskMapper.markRetryFailed(task.getId(), errorMessage, nextRetryAt);
        log.info("补偿任务将在 {} 秒后重试: taskId={}, retryCount={}",
                delaySeconds, task.getId(), nextRetryCount);
    }

    // ==================== 成员配额辅助方法 ====================

    /**
     * 使用成员配额
     */
    private boolean useMemberQuota(String workspaceId, String userId, long amount) {
        try {
            Result<Boolean> result = walletFeignClient.useQuota(workspaceId, userId, amount);
            if (result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
            // 区分"配额真正不足"和"系统繁忙"：系统繁忙应透传异常，不应误判为配额不足
            String message = result.getMessage();
            if (message != null && message.contains("系统繁忙")) {
                log.warn("配额服务繁忙（锁竞争），向上透传: workspaceId={}, userId={}, amount={}",
                        workspaceId, userId, amount);
                throw new BusinessException(ResultCode.CONCURRENT_OPERATION.getCode(),
                        "系统繁忙，请稍后重试");
            }
            log.warn("配额使用返回失败: workspaceId={}, userId={}, amount={}, message={}",
                    workspaceId, userId, amount, message);
            return false;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("配额使用服务调用失败: workspaceId={}, userId={}, amount={}, error={}",
                    workspaceId, userId, amount, e.getMessage());
            return false;
        }
    }

    /**
     * 发送单条补偿任务耗尽告警
     */
    private void sendCompensationExhaustedAlert(CompensationTask task, String errorMessage) {
        try {
            Map<String, Object> alertPayload = new HashMap<>();
            alertPayload.put("alertType", "COMPENSATION_EXHAUSTED");
            alertPayload.put("alertLevel", "CRITICAL");
            alertPayload.put("source", "actionow-task");
            alertPayload.put("compensationTaskId", task.getId());
            alertPayload.put("compensationType", task.getType());
            alertPayload.put("workspaceId", task.getWorkspaceId());
            alertPayload.put("lastError", errorMessage);
            alertPayload.put("retryCount", task.getRetryCount());
            alertPayload.put("message", String.format(
                    "补偿任务 [%s] 重试 %d 次后仍然失败 (type=%s, workspace=%s)。积分可能处于异常冻结状态，请人工介入。",
                    task.getId(), runtimeConfig.getCompensationMaxRetryCount(),
                    task.getType(), task.getWorkspaceId()));
            alertPayload.put("timestamp", LocalDateTime.now().toString());

            MessageWrapper<Map<String, Object>> message = MessageWrapper.wrap(
                    MqConstants.Alert.MSG_TYPE, alertPayload);
            messageProducer.send(MqConstants.EXCHANGE_DIRECT, MqConstants.Alert.ROUTING, message);
        } catch (Exception e) {
            log.error("发送补偿任务耗尽告警失败: taskId={}", task.getId(), e);
        }
    }

    /**
     * 退还成员配额
     *
     * @return true 退还成功，false 退还失败
     */
    private boolean refundMemberQuota(String workspaceId, String userId, long amount) {
        try {
            Result<Boolean> result = walletFeignClient.refundQuota(workspaceId, userId, amount);
            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("配额退还成功: workspaceId={}, userId={}, amount={}",
                        workspaceId, userId, amount);
                return true;
            }
            log.warn("配额退还返回失败: workspaceId={}, userId={}, amount={}, message={}",
                    workspaceId, userId, amount, result.getMessage());
            return false;
        } catch (Exception e) {
            log.error("配额退还服务调用失败: workspaceId={}, userId={}, amount={}, error={}",
                    workspaceId, userId, amount, e.getMessage());
            return false;
        }
    }
}
