package com.actionow.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 成员配额实体
 * 对应数据库表: t_member_quota
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("public.t_member_quota")
public class MemberQuota extends BaseEntity {

    /**
     * 工作空间ID
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 配额上限（单位：积分）
     * -1 表示无限制
     */
    @TableField("limit_amount")
    private Long limitAmount;

    /**
     * 已使用额度
     */
    @TableField("used_amount")
    private Long usedAmount;

    /**
     * 重置周期：DAILY, WEEKLY, MONTHLY, NEVER
     */
    @TableField("reset_cycle")
    private String resetCycle;

    /**
     * 上次重置时间
     */
    @TableField("last_reset_at")
    private LocalDateTime lastResetAt;

    /**
     * 获取剩余配额
     * -1 表示无限制
     */
    public long getRemainingQuota() {
        if (limitAmount == null || limitAmount < 0) {
            return -1L; // 无限制
        }
        return Math.max(0, limitAmount - (usedAmount != null ? usedAmount : 0));
    }

    /**
     * 检查配额是否充足
     */
    public boolean hasEnoughQuota(long amount) {
        if (limitAmount == null || limitAmount < 0) {
            return true; // 无限制
        }
        return getRemainingQuota() >= amount;
    }

    /**
     * 重置周期枚举
     */
    public static final class ResetCycle {
        public static final String DAILY = "DAILY";
        public static final String WEEKLY = "WEEKLY";
        public static final String MONTHLY = "MONTHLY";
        public static final String NEVER = "NEVER";

        private ResetCycle() {}
    }
}
