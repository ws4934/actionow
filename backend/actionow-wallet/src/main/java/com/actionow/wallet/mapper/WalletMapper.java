package com.actionow.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.wallet.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 钱包 Mapper
 * 对应表: t_workspace_wallet
 *
 * @author Actionow
 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {

    /**
     * 根据工作空间ID查询钱包
     */
    @Select("SELECT * FROM public.t_workspace_wallet WHERE workspace_id = #{workspaceId} AND deleted = 0")
    Wallet selectByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 增加余额（充值）
     */
    @Update("UPDATE public.t_workspace_wallet SET balance = balance + #{amount}, total_recharged = total_recharged + #{amount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{walletId} AND deleted = 0 AND version = #{version}")
    int increaseBalance(@Param("walletId") String walletId,
                        @Param("amount") long amount,
                        @Param("version") int version);

    /**
     * 扣减余额（消费）
     */
    @Update("UPDATE public.t_workspace_wallet SET balance = balance - #{amount}, total_consumed = total_consumed + #{amount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{walletId} AND balance >= #{amount} AND deleted = 0 AND version = #{version}")
    int decreaseBalance(@Param("walletId") String walletId,
                        @Param("amount") long amount,
                        @Param("version") int version);

    /**
     * 冻结金额
     */
    @Update("UPDATE public.t_workspace_wallet SET balance = balance - #{amount}, frozen = frozen + #{amount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{walletId} AND balance >= #{amount} AND deleted = 0 AND version = #{version}")
    int freezeAmount(@Param("walletId") String walletId,
                     @Param("amount") long amount,
                     @Param("version") int version);

    /**
     * 解冻金额（退回余额）
     */
    @Update("UPDATE public.t_workspace_wallet SET balance = balance + #{amount}, frozen = frozen - #{amount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{walletId} AND frozen >= #{amount} AND deleted = 0 AND version = #{version}")
    int unfreezeAmount(@Param("walletId") String walletId,
                       @Param("amount") long amount,
                       @Param("version") int version);

    /**
     * 确认消费（从冻结金额扣除）
     */
    @Update("UPDATE public.t_workspace_wallet SET frozen = frozen - #{amount}, total_consumed = total_consumed + #{amount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{walletId} AND frozen >= #{amount} AND deleted = 0 AND version = #{version}")
    int confirmConsume(@Param("walletId") String walletId,
                       @Param("amount") long amount,
                       @Param("version") int version);

    /**
     * 关闭钱包：将所有冻结金额退回余额，并标记状态为 CLOSED
     */
    @Update("UPDATE public.t_workspace_wallet SET balance = balance + frozen, frozen = 0, status = 'CLOSED', " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE workspace_id = #{workspaceId} AND deleted = 0")
    int closeWallet(@Param("workspaceId") String workspaceId);
}
