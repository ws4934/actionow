package com.actionow.wallet.dto;

import com.actionow.wallet.entity.MemberQuota;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 配额响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaResponse {

    /**
     * 配额ID
     */
    private String id;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 配额上限（-1表示无限制）
     */
    private Long limitAmount;

    /**
     * 已使用额度
     */
    private Long usedAmount;

    /**
     * 剩余额度（-1表示无限制）
     */
    private Long remainingAmount;

    /**
     * 使用百分比
     */
    private Double usagePercentage;

    /**
     * 重置周期: DAILY, WEEKLY, MONTHLY, NEVER
     */
    private String resetCycle;

    /**
     * 上次重置时间
     */
    private LocalDateTime lastResetAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换
     */
    public static QuotaResponse fromEntity(MemberQuota quota) {
        if (quota == null) {
            return null;
        }
        long remaining = quota.getRemainingQuota();
        long limitAmount = quota.getLimitAmount() != null ? quota.getLimitAmount() : -1L;
        long usedAmount = quota.getUsedAmount() != null ? quota.getUsedAmount() : 0L;

        double percentage = 0;
        if (limitAmount > 0) {
            percentage = (double) usedAmount / limitAmount * 100;
        }

        return QuotaResponse.builder()
                .id(quota.getId())
                .workspaceId(quota.getWorkspaceId())
                .userId(quota.getUserId())
                .limitAmount(limitAmount)
                .usedAmount(usedAmount)
                .remainingAmount(remaining)
                .usagePercentage(Math.round(percentage * 100) / 100.0)
                .resetCycle(quota.getResetCycle())
                .lastResetAt(quota.getLastResetAt())
                .createdAt(quota.getCreatedAt())
                .updatedAt(quota.getUpdatedAt())
                .build();
    }
}
