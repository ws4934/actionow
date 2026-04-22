package com.actionow.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 冻结流水实体
 * 对应数据库表: t_frozen_transaction
 * 注意：此表只有 created_at, updated_at，没有完整审计字段，不继承 BaseEntity
 *
 * @author Actionow
 */
@Data
@TableName("public.t_frozen_transaction")
public class FrozenTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 冻结流水ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 工作空间ID
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 用户ID（执行冻结操作的用户）
     */
    @TableField("user_id")
    private String userId;

    /**
     * 冻结金额（必须大于0）
     */
    private Long amount;

    /**
     * 冻结原因
     */
    private String reason;

    /**
     * 关联任务ID
     */
    @TableField("related_task_id")
    private String relatedTaskId;

    /**
     * 状态: FROZEN, UNFROZEN, CONSUMED, EXPIRED
     */
    private String status;

    /**
     * 过期时间
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * 解冻时间
     */
    @TableField("unfrozen_at")
    private LocalDateTime unfrozenAt;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 状态常量
     */
    public static final class Status {
        public static final String FROZEN = "FROZEN";
        public static final String UNFROZEN = "UNFROZEN";
        public static final String CONSUMED = "CONSUMED";
        public static final String EXPIRED = "EXPIRED";

        private Status() {}
    }
}
