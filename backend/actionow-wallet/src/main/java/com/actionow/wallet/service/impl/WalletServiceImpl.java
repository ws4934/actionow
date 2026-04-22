package com.actionow.wallet.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.redis.lock.DistributedLock;
import com.actionow.wallet.enums.WalletErrorCode;
import com.actionow.wallet.constant.WalletConstants;
import com.actionow.wallet.dto.*;
import com.actionow.wallet.entity.FrozenTransaction;
import com.actionow.wallet.entity.PointTransaction;
import com.actionow.wallet.entity.Wallet;
import com.actionow.wallet.mapper.FrozenTransactionMapper;
import com.actionow.wallet.mapper.PointTransactionMapper;
import com.actionow.wallet.mapper.WalletMapper;
import com.actionow.wallet.service.WalletService;
import com.actionow.wallet.websocket.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 钱包服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletMapper walletMapper;
    private final PointTransactionMapper pointTransactionMapper;
    private final FrozenTransactionMapper frozenTransactionMapper;
    private final DistributedLock distributedLock;
    private final WalletNotificationService walletNotificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletResponse getOrCreateWallet(String workspaceId) {
        Wallet wallet = walletMapper.selectByWorkspaceId(workspaceId);
        if (wallet == null) {
            wallet = createWallet(workspaceId);
        }
        return WalletResponse.fromEntity(wallet);
    }

    @Override
    public WalletResponse getBalance(String workspaceId) {
        Wallet wallet = getWalletOrThrow(workspaceId);
        return WalletResponse.fromEntity(wallet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse topup(String workspaceId, TopupRequest request, String operatorId) {
        String lockKey = WalletConstants.LOCK_WALLET_PREFIX + workspaceId;

        return distributedLock.executeWithLock(lockKey, 10, () -> {
            // 幂等保护：同一 paymentOrderId 只允许入账一次
            if (request.getPaymentOrderId() != null && !request.getPaymentOrderId().isBlank()) {
                PointTransaction existing = pointTransactionMapper.selectTopupByPaymentId(workspaceId, request.getPaymentOrderId());
                if (existing != null) {
                    log.info("检测到重复充值请求，返回已有交易: workspaceId={}, paymentOrderId={}, transactionId={}",
                            workspaceId, request.getPaymentOrderId(), existing.getId());
                    return TransactionResponse.fromEntity(existing);
                }
            }

            getWalletOrThrow(workspaceId); // 验证钱包存在且未关闭

            // 更新余额（带自旋重试）
            retryUpdate(workspaceId,
                    w -> walletMapper.increaseBalance(w.getId(), request.getAmount(), w.getVersion()),
                    WalletErrorCode.TRANSACTION_FAILED);

            // 重新读取钱包以获取准确余额（避免 retryUpdate 版本冲突重试时快照过期）
            Wallet topupUpdated = walletMapper.selectByWorkspaceId(workspaceId);
            long topupBalanceAfter = topupUpdated.getBalance();
            long topupBalanceBefore = topupBalanceAfter - request.getAmount();

            // 创建交易记录
            PointTransaction transaction = new PointTransaction();
            transaction.setId(UuidGenerator.generateUuidV7());
            transaction.setWorkspaceId(workspaceId);
            transaction.setOperatorId(operatorId);
            transaction.setTransactionType(PointTransaction.TransactionType.TOPUP);
            transaction.setAmount(request.getAmount());
            transaction.setBalanceBefore(topupBalanceBefore);
            transaction.setBalanceAfter(topupBalanceAfter);
            transaction.setDescription(request.getDescription());
            transaction.setCreatedAt(LocalDateTime.now());

            // 设置充值元数据
            if (request.getPaymentOrderId() != null) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("paymentId", request.getPaymentOrderId());
                meta.put("paymentMethod", request.getPaymentMethod());
                transaction.setMeta(meta);
            }

            pointTransactionMapper.insert(transaction);

            log.info("钱包充值成功: workspaceId={}, amount={}, balanceAfter={}",
                    workspaceId, request.getAmount(), transaction.getBalanceAfter());

            walletNotificationService.notifyBalanceChanged(
                    workspaceId, topupUpdated.getBalance(), topupUpdated.getFrozen(),
                    request.getAmount(), "TOPUP", transaction.getId());

            return TransactionResponse.fromEntity(transaction);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse consume(String workspaceId, ConsumeRequest request, String operatorId) {
        String lockKey = WalletConstants.LOCK_WALLET_PREFIX + workspaceId;

        return distributedLock.executeWithLock(lockKey, 10, () -> {
            Wallet wallet = getWalletOrThrow(workspaceId);
            checkBalance(wallet, request.getAmount());

            // 扣减余额（带自旋重试）
            retryUpdate(workspaceId,
                    w -> walletMapper.decreaseBalance(w.getId(), request.getAmount(), w.getVersion()),
                    WalletErrorCode.BALANCE_NOT_ENOUGH);

            // 重新读取钱包以获取准确余额（避免 retryUpdate 版本冲突重试时快照过期）
            Wallet consumeUpdated = walletMapper.selectByWorkspaceId(workspaceId);
            long consumeBalanceAfter = consumeUpdated.getBalance();
            long consumeBalanceBefore = consumeBalanceAfter + request.getAmount();

            // 创建交易记录
            PointTransaction transaction = new PointTransaction();
            transaction.setId(UuidGenerator.generateUuidV7());
            transaction.setWorkspaceId(workspaceId);
            transaction.setUserId(request.getUserId());
            transaction.setOperatorId(operatorId);
            transaction.setTransactionType(PointTransaction.TransactionType.CONSUME);
            transaction.setAmount(-request.getAmount()); // 消费为负数
            transaction.setBalanceBefore(consumeBalanceBefore);
            transaction.setBalanceAfter(consumeBalanceAfter);
            transaction.setDescription(request.getDescription());
            transaction.setRelatedTaskId(request.getBusinessId());
            transaction.setMeta(request.getMetadata());
            transaction.setCreatedAt(LocalDateTime.now());
            pointTransactionMapper.insert(transaction);

            log.info("钱包消费成功: workspaceId={}, amount={}, businessId={}",
                    workspaceId, request.getAmount(), request.getBusinessId());

            walletNotificationService.notifyBalanceChanged(
                    workspaceId, consumeUpdated.getBalance(), consumeUpdated.getFrozen(),
                    -request.getAmount(), "CONSUME", transaction.getId());

            return TransactionResponse.fromEntity(transaction);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse freeze(String workspaceId, FreezeRequest request, String operatorId) {
        String lockKey = WalletConstants.LOCK_WALLET_PREFIX + workspaceId;

        return distributedLock.executeWithLock(lockKey, 10, () -> {
            Wallet wallet = getWalletOrThrow(workspaceId);
            checkBalance(wallet, request.getAmount());

            // 冻结金额（带自旋重试）
            retryUpdate(workspaceId,
                    w -> walletMapper.freezeAmount(w.getId(), request.getAmount(), w.getVersion()),
                    WalletErrorCode.BALANCE_NOT_ENOUGH);

            // 重新读取钱包以获取准确余额（避免 retryUpdate 版本冲突重试时快照过期）
            Wallet freezeUpdated = walletMapper.selectByWorkspaceId(workspaceId);
            long freezeBalanceAfter = freezeUpdated.getBalance();
            long freezeBalanceBefore = freezeBalanceAfter + request.getAmount();

            // 创建冻结流水记录
            FrozenTransaction frozenTransaction = new FrozenTransaction();
            frozenTransaction.setId(UuidGenerator.generateUuidV7());
            frozenTransaction.setWorkspaceId(workspaceId);
            frozenTransaction.setUserId(request.getUserId() != null ? request.getUserId() : operatorId);
            frozenTransaction.setAmount(request.getAmount());
            frozenTransaction.setReason(request.getReason());
            frozenTransaction.setRelatedTaskId(request.getBusinessId());
            frozenTransaction.setStatus(FrozenTransaction.Status.FROZEN);
            frozenTransaction.setCreatedAt(LocalDateTime.now());
            frozenTransaction.setUpdatedAt(LocalDateTime.now());
            frozenTransactionMapper.insert(frozenTransaction);

            // 创建积分流水记录
            PointTransaction transaction = new PointTransaction();
            transaction.setId(UuidGenerator.generateUuidV7());
            transaction.setWorkspaceId(workspaceId);
            transaction.setUserId(request.getUserId());
            transaction.setOperatorId(operatorId);
            transaction.setTransactionType(PointTransaction.TransactionType.FREEZE);
            transaction.setAmount(-request.getAmount()); // 冻结为负数（从可用余额扣除）
            transaction.setBalanceBefore(freezeBalanceBefore);
            transaction.setBalanceAfter(freezeBalanceAfter);
            transaction.setDescription(request.getDescription());
            transaction.setRelatedTaskId(request.getBusinessId());
            transaction.setCreatedAt(LocalDateTime.now());
            pointTransactionMapper.insert(transaction);

            log.info("钱包冻结成功: workspaceId={}, amount={}, businessId={}",
                    workspaceId, request.getAmount(), request.getBusinessId());

            walletNotificationService.notifyBalanceChanged(
                    workspaceId, freezeUpdated.getBalance(), freezeUpdated.getFrozen(),
                    -request.getAmount(), "FREEZE", transaction.getId());

            return TransactionResponse.fromEntity(transaction);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse unfreeze(String workspaceId, String businessId, String businessType, String operatorId) {
        String lockKey = WalletConstants.LOCK_WALLET_PREFIX + workspaceId;

        return distributedLock.executeWithLock(lockKey, 10, () -> {
            // 查找该 businessId 下所有 FROZEN 状态的冻结记录（支持多次追加冻结场景）
            List<FrozenTransaction> frozenRecords = frozenTransactionMapper.selectAllByTaskId(businessId);
            if (frozenRecords.isEmpty()) {
                throw new BusinessException(WalletErrorCode.FROZEN_RECORD_NOT_FOUND);
            }
            // 汇总全部冻结金额
            long frozenAmt = frozenRecords.stream().mapToLong(FrozenTransaction::getAmount).sum();

            Wallet wallet = getWalletOrThrow(workspaceId);

            // 解冻汇总金额（带自旋重试）
            retryUpdate(workspaceId,
                    w -> walletMapper.unfreezeAmount(w.getId(), frozenAmt, w.getVersion()),
                    WalletErrorCode.UNFREEZE_FAILED);

            // 批量更新所有冻结记录状态
            frozenTransactionMapper.unfreezeAllByTaskId(businessId);

            // 刷新钱包获取准确余额
            wallet = walletMapper.selectByWorkspaceId(workspaceId);

            // 创建交易记录
            PointTransaction transaction = new PointTransaction();
            transaction.setId(UuidGenerator.generateUuidV7());
            transaction.setWorkspaceId(workspaceId);
            transaction.setOperatorId(operatorId);
            transaction.setTransactionType(PointTransaction.TransactionType.UNFREEZE);
            transaction.setAmount(frozenAmt); // 解冻为正数（退回可用余额）
            transaction.setBalanceBefore(wallet.getBalance() - frozenAmt);
            transaction.setBalanceAfter(wallet.getBalance());
            transaction.setDescription("解冻退回");
            transaction.setRelatedTaskId(businessId);
            transaction.setCreatedAt(LocalDateTime.now());
            pointTransactionMapper.insert(transaction);

            log.info("钱包解冻成功: workspaceId={}, totalAmount={}, businessId={}, recordCount={}",
                    workspaceId, frozenAmt, businessId, frozenRecords.size());

            walletNotificationService.notifyBalanceChanged(
                    workspaceId, wallet.getBalance(), wallet.getFrozen(),
                    frozenAmt, "UNFREEZE", transaction.getId());

            return TransactionResponse.fromEntity(transaction);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse confirmConsume(String workspaceId, String businessId, String businessType,
                                              Long actualAmount, String operatorId) {
        String lockKey = WalletConstants.LOCK_WALLET_PREFIX + workspaceId;

        return distributedLock.executeWithLock(lockKey, 10, () -> {
            // 查找该 businessId 下所有 FROZEN 状态的冻结记录（支持多次追加冻结场景）
            List<FrozenTransaction> frozenRecords = frozenTransactionMapper.selectAllByTaskId(businessId);
            if (frozenRecords.isEmpty()) {
                throw new BusinessException(WalletErrorCode.FROZEN_RECORD_NOT_FOUND);
            }

            Wallet wallet = getWalletOrThrow(workspaceId);
            // 汇总全部冻结金额
            long frozenAmount = frozenRecords.stream().mapToLong(FrozenTransaction::getAmount).sum();
            long consumeAmount = actualAmount != null ? actualAmount : frozenAmount;

            // Fix 8: 显式校验实际消费不超过冻结总额，避免 frozen 字段下溢
            if (consumeAmount > frozenAmount) {
                throw new BusinessException(WalletErrorCode.CONFIRM_CONSUME_FAILED,
                        "实际消费金额 " + consumeAmount + " 超过冻结总额 " + frozenAmount);
            }

            // 如果实际消费小于冻结总额，差额退回（带自旋重试）
            if (consumeAmount < frozenAmount) {
                long refundAmount = frozenAmount - consumeAmount;
                retryUpdate(workspaceId,
                        w -> walletMapper.unfreezeAmount(w.getId(), refundAmount, w.getVersion()),
                        WalletErrorCode.UNFREEZE_FAILED);

                // Fix 6: 为部分退款创建 UNFREEZE 流水，保证账单历史完整
                Wallet refundWallet = walletMapper.selectByWorkspaceId(workspaceId);
                PointTransaction refundTxn = new PointTransaction();
                refundTxn.setId(UuidGenerator.generateUuidV7());
                refundTxn.setWorkspaceId(workspaceId);
                refundTxn.setUserId(frozenRecords.get(0).getUserId());
                refundTxn.setOperatorId(operatorId);
                refundTxn.setTransactionType(PointTransaction.TransactionType.UNFREEZE);
                refundTxn.setAmount(refundAmount);
                refundTxn.setBalanceBefore(refundWallet.getBalance() - refundAmount);
                refundTxn.setBalanceAfter(refundWallet.getBalance());
                refundTxn.setDescription("冻结差额退回");
                refundTxn.setRelatedTaskId(businessId);
                refundTxn.setCreatedAt(LocalDateTime.now());
                pointTransactionMapper.insert(refundTxn);
            }

            // 确认消费（带自旋重试）
            retryUpdate(workspaceId,
                    w -> walletMapper.confirmConsume(w.getId(), consumeAmount, w.getVersion()),
                    WalletErrorCode.CONFIRM_CONSUME_FAILED);

            // 批量更新所有冻结记录状态为已消费
            frozenTransactionMapper.consumeAllByTaskId(businessId);

            // 刷新钱包获取准确余额
            wallet = walletMapper.selectByWorkspaceId(workspaceId);

            // 创建 CONSUME 交易记录（余额视角：consumeAmount 最终从 frozen 中扣除）
            PointTransaction transaction = new PointTransaction();
            transaction.setId(UuidGenerator.generateUuidV7());
            transaction.setWorkspaceId(workspaceId);
            transaction.setUserId(frozenRecords.get(0).getUserId());
            transaction.setOperatorId(operatorId);
            transaction.setTransactionType(PointTransaction.TransactionType.CONSUME);
            transaction.setAmount(-consumeAmount); // 消费为负数
            transaction.setBalanceBefore(wallet.getBalance() + consumeAmount);
            transaction.setBalanceAfter(wallet.getBalance());
            transaction.setDescription("冻结转消费");
            transaction.setRelatedTaskId(businessId);
            transaction.setCreatedAt(LocalDateTime.now());
            pointTransactionMapper.insert(transaction);

            log.info("确认消费成功: workspaceId={}, frozenTotal={}, actualAmount={}, businessId={}, recordCount={}",
                    workspaceId, frozenAmount, consumeAmount, businessId, frozenRecords.size());

            walletNotificationService.notifyBalanceChanged(
                    workspaceId, wallet.getBalance(), wallet.getFrozen(),
                    -consumeAmount, "CONSUME", transaction.getId());

            return TransactionResponse.fromEntity(transaction);
        });
    }

    @Override
    public boolean hasEnoughBalance(String workspaceId, long amount) {
        Wallet wallet = walletMapper.selectByWorkspaceId(workspaceId);
        if (wallet == null) {
            return false;
        }
        // wallet.balance 始终是【可用余额】（freeze 时 balance 同步减少、frozen 增加，
        // 因此 balance 已扣除所有冻结中的金额），直接与 amount 比较即可。
        return wallet.getBalance() >= amount;
    }

    @Override
    public List<TransactionResponse> getTransactions(String workspaceId, int limit) {
        List<PointTransaction> transactions = pointTransactionMapper.selectByWorkspaceId(workspaceId, limit);
        return transactions.stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<TransactionResponse> getTransactionsPage(String workspaceId, Long current, Long size, String transactionType) {
        // 参数校验
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        // 分页查询
        Page<PointTransaction> page = new Page<>(current, size);
        IPage<PointTransaction> transactionPage = pointTransactionMapper.selectPageByWorkspaceId(page, workspaceId, transactionType);

        if (transactionPage.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        // 转换响应
        List<TransactionResponse> records = transactionPage.getRecords().stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(transactionPage.getCurrent(), transactionPage.getSize(), transactionPage.getTotal(), records);
    }

    @Override
    public List<TransactionResponse> getTransactionsByBusiness(String businessId, String businessType) {
        List<PointTransaction> transactions = pointTransactionMapper.selectByTaskId(businessId);
        return transactions.stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getStatistics(String workspaceId, String startDate, String endDate) {
        LocalDateTime startTime = startDate != null
                ? LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE).atStartOfDay()
                : LocalDateTime.now().minusMonths(1);
        LocalDateTime endTime = endDate != null
                ? LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE).atTime(LocalTime.MAX)
                : LocalDateTime.now();

        Wallet wallet = walletMapper.selectByWorkspaceId(workspaceId);
        Long totalConsume = pointTransactionMapper.sumConsumeByWorkspace(workspaceId);
        List<Map<String, Object>> byType = pointTransactionMapper.sumByType(workspaceId, startTime, endTime);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("balance", wallet != null ? wallet.getBalance() : 0);
        statistics.put("frozen", wallet != null ? wallet.getFrozen() : 0);
        statistics.put("totalRecharged", wallet != null ? wallet.getTotalRecharged() : 0);
        statistics.put("totalConsumed", totalConsume);
        statistics.put("byType", byType);
        statistics.put("startDate", startTime);
        statistics.put("endDate", endTime);

        return statistics;
    }

    /**
     * 创建钱包
     */
    private Wallet createWallet(String workspaceId) {
        Wallet wallet = new Wallet();
        wallet.setId(UuidGenerator.generateUuidV7());
        wallet.setWorkspaceId(workspaceId);
        wallet.setBalance(WalletConstants.DEFAULT_INITIAL_BALANCE);
        wallet.setFrozen(0L);
        wallet.setTotalRecharged(0L);
        wallet.setTotalConsumed(0L);
        wallet.setStatus(WalletConstants.WalletStatus.ACTIVE);
        walletMapper.insert(wallet);
        log.info("创建钱包成功: workspaceId={}", workspaceId);
        return wallet;
    }

    /**
     * 获取钱包或抛出异常
     */
    private Wallet getWalletOrThrow(String workspaceId) {
        Wallet wallet = walletMapper.selectByWorkspaceId(workspaceId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        if (wallet.isClosed()) {
            throw new BusinessException(WalletErrorCode.WALLET_CLOSED);
        }
        return wallet;
    }

    /**
     * 检查余额
     */
    private void checkBalance(Wallet wallet, long amount) {
        if (wallet.getBalance() < amount) {
            throw new BusinessException(WalletErrorCode.BALANCE_NOT_ENOUGH);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeWallet(String workspaceId, String operatorId) {
        String lockKey = WalletConstants.LOCK_WALLET_PREFIX + workspaceId;
        distributedLock.executeWithLock(lockKey, 10, () -> {
            Wallet wallet = walletMapper.selectByWorkspaceId(workspaceId);
            if (wallet == null) {
                log.warn("关闭钱包时未找到钱包记录（可能从未创建）: workspaceId={}", workspaceId);
                return null;
            }
            if (wallet.isClosed()) {
                log.warn("钱包已关闭，跳过重复关闭: workspaceId={}", workspaceId);
                return null;
            }

            long frozenBefore = wallet.getFrozen() != null ? wallet.getFrozen() : 0L;
            int updated = walletMapper.closeWallet(workspaceId);
            if (updated == 0) {
                log.error("关闭钱包失败: workspaceId={}", workspaceId);
                throw new BusinessException(WalletErrorCode.TRANSACTION_FAILED);
            }

            // 若有冻结金额，记录解冻流水（资金回归余额后标记为关闭）
            if (frozenBefore > 0) {
                PointTransaction transaction = new PointTransaction();
                transaction.setId(UuidGenerator.generateUuidV7());
                transaction.setWorkspaceId(workspaceId);
                transaction.setOperatorId(operatorId);
                transaction.setTransactionType(PointTransaction.TransactionType.UNFREEZE);
                transaction.setAmount(frozenBefore);
                transaction.setBalanceBefore(wallet.getBalance());
                transaction.setBalanceAfter(wallet.getBalance() + frozenBefore);
                transaction.setDescription("工作空间解散，自动解冻所有冻结金额");
                transaction.setCreatedAt(LocalDateTime.now());
                pointTransactionMapper.insert(transaction);
            }

            log.info("钱包关闭成功: workspaceId={}, frozenRefunded={}, operatorId={}",
                    workspaceId, frozenBefore, operatorId);
            return null;
        });
    }

    /**
     * 带自旋重试的乐观锁更新。
     * 当 updated == 0（version 冲突）时重新读取最新钱包并重试，最多 3 次。
     * 若仍失败则抛出 errorCode 对应的异常。
     *
     * @param workspaceId 工作空间 ID（用于重新读取）
     * @param updateFn    接收最新 Wallet 并执行更新的函数，返回受影响行数
     * @param errorCode   全部重试失败后抛出的错误码
     */
    private void retryUpdate(String workspaceId,
                             Function<Wallet, Integer> updateFn,
                             WalletErrorCode errorCode) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Wallet fresh = walletMapper.selectByWorkspaceId(workspaceId);
            if (fresh == null) {
                throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
            }
            int updated = updateFn.apply(fresh);
            if (updated > 0) {
                return;
            }
            log.warn("乐观锁冲突，重试 [{}/{}]: workspaceId={}", attempt + 1, maxRetries, workspaceId);
        }
        throw new BusinessException(errorCode);
    }
}
