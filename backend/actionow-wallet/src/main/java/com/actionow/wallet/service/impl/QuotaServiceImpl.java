package com.actionow.wallet.service.impl;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.redis.lock.DistributedLock;
import com.actionow.wallet.enums.WalletErrorCode;
import com.actionow.wallet.constant.WalletConstants;
import com.actionow.wallet.dto.QuotaRequest;
import com.actionow.wallet.dto.QuotaResponse;
import com.actionow.wallet.entity.MemberQuota;
import com.actionow.wallet.mapper.MemberQuotaMapper;
import com.actionow.wallet.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 成员配额服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final MemberQuotaMapper quotaMapper;
    private final DistributedLock distributedLock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotaResponse setQuota(String workspaceId, QuotaRequest request, String operatorId) {
        MemberQuota quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, request.getUserId());

        if (quota == null) {
            // 创建新配额
            quota = createQuota(workspaceId, request.getUserId(), request.getLimitAmount(), request.getResetCycle());
        } else {
            // 更新配额
            quota.setLimitAmount(request.getLimitAmount());
            if (request.getResetCycle() != null) {
                quota.setResetCycle(request.getResetCycle());
            }
            quotaMapper.updateById(quota);
        }

        log.info("设置成员配额: workspaceId={}, userId={}, limitAmount={}, resetCycle={}, operatorId={}",
                workspaceId, request.getUserId(), request.getLimitAmount(), request.getResetCycle(), operatorId);

        return QuotaResponse.fromEntity(quota);
    }

    @Override
    public QuotaResponse getQuota(String workspaceId, String userId) {
        MemberQuota quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
        return QuotaResponse.fromEntity(quota);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotaResponse getOrCreateQuota(String workspaceId, String userId) {
        MemberQuota quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
        if (quota == null) {
            quota = createQuota(workspaceId, userId, WalletConstants.DEFAULT_MEMBER_QUOTA, MemberQuota.ResetCycle.MONTHLY);
        }

        // 检查是否需要重置
        if (shouldResetQuota(quota)) {
            resetQuotaUsage(quota);
            quota = quotaMapper.selectById(quota.getId());
        }

        return QuotaResponse.fromEntity(quota);
    }

    @Override
    public List<QuotaResponse> listQuotas(String workspaceId) {
        List<MemberQuota> quotas = quotaMapper.selectByWorkspaceId(workspaceId);
        return quotas.stream()
                .map(QuotaResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasEnoughQuota(String workspaceId, String userId, long amount) {
        MemberQuota quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
        if (quota == null) {
            return true; // 没有配额限制，允许使用
        }

        // 无限制配额
        if (quota.getLimitAmount() == null || quota.getLimitAmount() < 0) {
            return true;
        }

        // 检查是否需要重置（自动重置后允许使用）
        if (shouldResetQuota(quota)) {
            return true;
        }

        return quota.hasEnoughQuota(amount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean useQuota(String workspaceId, String userId, long amount) {
        MemberQuota quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
        if (quota == null) {
            return true; // 没有配额限制
        }

        // 无限制配额
        if (quota.getLimitAmount() == null || quota.getLimitAmount() < 0) {
            return true;
        }

        // 仅在需要周期重置时加锁（极低频），正常扣减走乐观锁无需分布式锁
        if (shouldResetQuota(quota)) {
            String resetLockKey = WalletConstants.LOCK_WALLET_PREFIX + "quota:reset:" + workspaceId + ":" + userId;
            distributedLock.executeWithLock(resetLockKey, 5, () -> {
                MemberQuota lockedQuota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
                if (lockedQuota != null && shouldResetQuota(lockedQuota)) {
                    resetQuotaUsage(lockedQuota);
                    log.info("配额周期重置完成: workspaceId={}, userId={}", workspaceId, userId);
                }
            });
            // 重置后重新读取
            quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
            if (quota == null) {
                return true;
            }
        }

        // 乐观锁扣减（无分布式锁，并发安全）
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (!quota.hasEnoughQuota(amount)) {
                log.warn("配额不足: workspaceId={}, userId={}, amount={}, remaining={}",
                        workspaceId, userId, amount, quota.getRemainingQuota());
                return false;
            }

            int updated = quotaMapper.increaseUsedAmount(quota.getId(), amount, quota.getVersion());
            if (updated > 0) {
                log.debug("配额使用成功: workspaceId={}, userId={}, amount={}", workspaceId, userId, amount);
                return true;
            }
            // version 冲突，重读重试
            log.warn("配额乐观锁冲突，重试 [{}/{}]: workspaceId={}, userId={}",
                    attempt + 1, maxRetries, workspaceId, userId);
            quota = quotaMapper.selectById(quota.getId());
            if (quota == null) {
                return true;
            }
        }
        log.error("配额使用失败（超过最大重试次数）: workspaceId={}, userId={}", workspaceId, userId);
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundQuota(String workspaceId, String userId, long amount) {
        // 使用与 useQuota 相同的锁，保证 check-deduct 与 refund 串行执行
        String lockKey = WalletConstants.LOCK_WALLET_PREFIX + "quota:reset:" + workspaceId + ":" + userId;
        return distributedLock.executeWithLock(lockKey, 5, () -> {
            MemberQuota quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
            if (quota == null) {
                return true; // 没有配额记录，无需退还
            }

            int updated = quotaMapper.decreaseUsedAmount(quota.getId(), amount, quota.getVersion());
            if (updated == 0) {
                log.warn("配额退还失败: workspaceId={}, userId={}, amount={}", workspaceId, userId, amount);
                return false;
            }

            log.debug("配额退还成功: workspaceId={}, userId={}, amount={}", workspaceId, userId, amount);
            return true;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuota(String workspaceId, String userId, String operatorId) {
        // 使用物理删除而非软删除
        // 原因：t_member_quota 有 UNIQUE(workspace_id, user_id) 约束，软删除后
        // 成员重新加入时无法创建新 quota 记录（会违反唯一约束）
        int deleted = quotaMapper.hardDeleteByWorkspaceAndUser(workspaceId, userId);
        if (deleted > 0) {
            log.info("删除成员配额（物理删除）: workspaceId={}, userId={}, operatorId={}",
                    workspaceId, userId, operatorId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotaResponse resetQuota(String workspaceId, String userId, String operatorId) {
        MemberQuota quota = quotaMapper.selectByWorkspaceAndUser(workspaceId, userId);
        if (quota == null) {
            throw new BusinessException(WalletErrorCode.QUOTA_NOT_FOUND);
        }

        resetQuotaUsage(quota);
        quota = quotaMapper.selectById(quota.getId());

        log.info("重置成员配额: workspaceId={}, userId={}, operatorId={}", workspaceId, userId, operatorId);
        return QuotaResponse.fromEntity(quota);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetWorkspaceQuotas(String workspaceId) {
        int updated = quotaMapper.resetQuotaByWorkspace(workspaceId);
        log.info("重置工作空间配额: workspaceId={}, count={}", workspaceId, updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustQuotasForPlan(String workspaceId, String planType) {
        long newLimit = WalletConstants.PlanQuota.getByPlanType(planType);
        int updated = quotaMapper.updateLimitByWorkspace(workspaceId, newLimit);
        log.info("计划变更，批量调整成员配额上限: workspaceId={}, planType={}, newLimit={}, updatedCount={}",
                workspaceId, planType, newLimit, updated);
    }

    /**
     * 创建配额
     */
    private MemberQuota createQuota(String workspaceId, String userId, long limitAmount, String resetCycle) {
        MemberQuota quota = new MemberQuota();
        quota.setId(UuidGenerator.generateUuidV7());
        quota.setWorkspaceId(workspaceId);
        quota.setUserId(userId);
        quota.setLimitAmount(limitAmount);
        quota.setUsedAmount(0L);
        quota.setResetCycle(resetCycle != null ? resetCycle : MemberQuota.ResetCycle.MONTHLY);
        quota.setLastResetAt(LocalDateTime.now());
        quotaMapper.insert(quota);

        log.info("创建成员配额: workspaceId={}, userId={}, limitAmount={}, resetCycle={}",
                workspaceId, userId, limitAmount, resetCycle);
        return quota;
    }

    /**
     * 检查是否需要重置配额
     */
    private boolean shouldResetQuota(MemberQuota quota) {
        if (quota.getLastResetAt() == null) {
            return true;
        }

        String resetCycle = quota.getResetCycle();
        if (resetCycle == null || MemberQuota.ResetCycle.NEVER.equals(resetCycle)) {
            return false;
        }

        LocalDateTime lastReset = quota.getLastResetAt();
        LocalDateTime now = LocalDateTime.now();

        return switch (resetCycle) {
            case MemberQuota.ResetCycle.DAILY -> lastReset.toLocalDate().isBefore(now.toLocalDate());
            case MemberQuota.ResetCycle.WEEKLY -> lastReset.plusWeeks(1).isBefore(now);
            case MemberQuota.ResetCycle.MONTHLY -> lastReset.plusMonths(1).isBefore(now);
            default -> false;
        };
    }

    /**
     * 重置配额使用量
     */
    private void resetQuotaUsage(MemberQuota quota) {
        quota.setUsedAmount(0L);
        quota.setLastResetAt(LocalDateTime.now());
        quotaMapper.updateById(quota);

        log.info("重置配额使用量: quotaId={}, workspaceId={}, userId={}",
                quota.getId(), quota.getWorkspaceId(), quota.getUserId());
    }
}
