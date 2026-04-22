package com.actionow.wallet.dto;

import com.actionow.wallet.entity.PointTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交易流水响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    /**
     * 交易ID
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
     * 操作人ID
     */
    private String operatorId;

    /**
     * 交易类型
     */
    private String transactionType;

    /**
     * 交易金额
     */
    private Long amount;

    /**
     * 交易前余额
     */
    private Long balanceBefore;

    /**
     * 交易后余额
     */
    private Long balanceAfter;

    /**
     * 交易描述
     */
    private String description;

    /**
     * 关联任务ID
     */
    private String relatedTaskId;

    /**
     * 扩展信息
     */
    private Map<String, Object> meta;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 从实体转换
     */
    public static TransactionResponse fromEntity(PointTransaction transaction) {
        if (transaction == null) {
            return null;
        }
        return TransactionResponse.builder()
                .id(transaction.getId())
                .workspaceId(transaction.getWorkspaceId())
                .userId(transaction.getUserId())
                .operatorId(transaction.getOperatorId())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .balanceBefore(transaction.getBalanceBefore())
                .balanceAfter(transaction.getBalanceAfter())
                .description(transaction.getDescription())
                .relatedTaskId(transaction.getRelatedTaskId())
                .meta(transaction.getMeta())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
