package com.actionow.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 积分流水实体
 * 对应数据库表: t_point_transaction
 * 注意：此表只有 created_at，没有审计字段，不继承 BaseEntity
 *
 * @author Actionow
 */
@Data
@TableName(value = "public.t_point_transaction", autoResultMap = true)
public class PointTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流水ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 工作空间ID
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 用户ID（执行操作的用户）
     */
    @TableField("user_id")
    private String userId;

    /**
     * 操作人ID（管理员充值时）
     */
    @TableField("operator_id")
    private String operatorId;

    /**
     * 交易类型: TOPUP, CONSUME, REFUND, TRANSFER, FREEZE, UNFREEZE
     */
    @TableField("transaction_type")
    private String transactionType;

    /**
     * 变动金额（正增负减）
     */
    private Long amount;

    /**
     * 变动前余额
     */
    @TableField("balance_before")
    private Long balanceBefore;

    /**
     * 变动后余额
     */
    @TableField("balance_after")
    private Long balanceAfter;

    /**
     * 描述
     */
    private String description;

    /**
     * 关联任务ID
     */
    @TableField("related_task_id")
    private String relatedTaskId;

    /**
     * 扩展信息（JSONB）
     * 包含: taskType, modelName, inputTokens, outputTokens, imageCount, resolution, paymentId, paymentMethod
     */
    @TableField(value = "meta", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> meta;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 交易类型常量
     */
    public static final class TransactionType {
        public static final String TOPUP = "TOPUP";
        public static final String CONSUME = "CONSUME";
        public static final String REFUND = "REFUND";
        public static final String TRANSFER = "TRANSFER";
        public static final String FREEZE = "FREEZE";
        public static final String UNFREEZE = "UNFREEZE";

        private TransactionType() {}
    }
}
