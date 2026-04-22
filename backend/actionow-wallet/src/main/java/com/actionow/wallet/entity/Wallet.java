package com.actionow.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import com.actionow.wallet.constant.WalletConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作空间钱包实体
 * 对应数据库表: t_workspace_wallet
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("public.t_workspace_wallet")
public class Wallet extends BaseEntity {

    /**
     * 工作空间ID（唯一）
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 可用余额（单位：积分）
     */
    private Long balance;

    /**
     * 冻结金额
     */
    private Long frozen;

    /**
     * 累计充值
     */
    @TableField("total_recharged")
    private Long totalRecharged;

    /**
     * 累计消费
     */
    @TableField("total_consumed")
    private Long totalConsumed;

    /**
     * 钱包状态：ACTIVE / FROZEN / CLOSED
     * @see WalletConstants.WalletStatus
     */
    private String status;

    /**
     * 获取可用余额（余额减去冻结）
     */
    public Long getAvailable() {
        return balance != null && frozen != null ? balance - frozen : balance;
    }

    /**
     * 是否已关闭
     */
    public boolean isClosed() {
        return WalletConstants.WalletStatus.CLOSED.equals(status);
    }
}
