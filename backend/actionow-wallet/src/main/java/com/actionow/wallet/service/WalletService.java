package com.actionow.wallet.service;

import com.actionow.common.core.result.PageResult;
import com.actionow.wallet.dto.*;

import java.util.List;
import java.util.Map;

/**
 * 钱包服务接口
 *
 * @author Actionow
 */
public interface WalletService {

    /**
     * 获取或创建工作空间钱包
     */
    WalletResponse getOrCreateWallet(String workspaceId);

    /**
     * 获取钱包余额
     */
    WalletResponse getBalance(String workspaceId);

    /**
     * 充值
     */
    TransactionResponse topup(String workspaceId, TopupRequest request, String operatorId);

    /**
     * 消费（直接扣费）
     */
    TransactionResponse consume(String workspaceId, ConsumeRequest request, String operatorId);

    /**
     * 冻结金额（预扣）
     */
    TransactionResponse freeze(String workspaceId, FreezeRequest request, String operatorId);

    /**
     * 解冻金额（退回可用余额）
     */
    TransactionResponse unfreeze(String workspaceId, String businessId, String businessType, String operatorId);

    /**
     * 确认消费（从冻结金额扣除）
     */
    TransactionResponse confirmConsume(String workspaceId, String businessId, String businessType,
                                       Long actualAmount, String operatorId);

    /**
     * 检查余额是否足够
     */
    boolean hasEnoughBalance(String workspaceId, long amount);

    /**
     * 获取交易记录
     */
    List<TransactionResponse> getTransactions(String workspaceId, int limit);

    /**
     * 分页获取交易记录
     *
     * @param workspaceId     工作空间ID
     * @param current         当前页码
     * @param size            每页大小
     * @param transactionType 交易类型（可选筛选）
     * @return 分页交易记录
     */
    PageResult<TransactionResponse> getTransactionsPage(String workspaceId, Long current, Long size, String transactionType);

    /**
     * 根据业务ID获取交易记录
     */
    List<TransactionResponse> getTransactionsByBusiness(String businessId, String businessType);

    /**
     * 获取钱包统计
     */
    Map<String, Object> getStatistics(String workspaceId, String startDate, String endDate);

    /**
     * 关闭工作空间钱包（Workspace 解散时调用）
     * 解冻所有冻结金额并将钱包状态标记为 CLOSED，防止后续操作
     *
     * @param workspaceId 工作空间 ID
     * @param operatorId  操作人 ID
     */
    void closeWallet(String workspaceId, String operatorId);
}
