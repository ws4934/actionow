package com.actionow.wallet.dto;

import com.actionow.wallet.entity.Wallet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 钱包响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    /**
     * 钱包ID
     */
    private String id;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 可用余额（已扣除冻结金额）
     */
    private Long balance;

    /**
     * 可用余额（balance的别名，符合设计规范）
     */
    private Long available;

    /**
     * 冻结金额
     */
    private Long frozen;

    /**
     * 总余额（可用 + 冻结）
     */
    private Long totalBalance;

    /**
     * 累计充值
     */
    private Long totalRecharged;

    /**
     * 累计消费
     */
    private Long totalConsumed;

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
    public static WalletResponse fromEntity(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        long balance = wallet.getBalance() != null ? wallet.getBalance() : 0L;
        long frozen = wallet.getFrozen() != null ? wallet.getFrozen() : 0L;
        return WalletResponse.builder()
                .id(wallet.getId())
                .workspaceId(wallet.getWorkspaceId())
                .balance(balance)
                .available(balance)  // balance即为可用余额
                .frozen(frozen)
                .totalBalance(balance + frozen)
                .totalRecharged(wallet.getTotalRecharged())
                .totalConsumed(wallet.getTotalConsumed())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
