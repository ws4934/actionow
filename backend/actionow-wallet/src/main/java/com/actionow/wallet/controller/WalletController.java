package com.actionow.wallet.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.wallet.dto.*;
import com.actionow.wallet.service.QuotaService;
import com.actionow.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 钱包控制器
 * workspaceId 从请求头获取（通过 UserContextHolder）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final QuotaService quotaService;

    // ==================== 钱包管理 ====================

    /**
     * 获取工作空间钱包余额
     * GET /wallet
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<WalletResponse> getBalance() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        WalletResponse response = walletService.getOrCreateWallet(workspaceId);
        return Result.success(response);
    }

    /**
     * 充值
     * POST /wallet/topup
     */
    @PostMapping("/topup")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<TransactionResponse> topup(@RequestBody @Valid TopupRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        TransactionResponse response = walletService.topup(workspaceId, request, operatorId);
        return Result.success(response);
    }

    /**
     * 检查余额是否足够
     * GET /wallet/check
     */
    @GetMapping("/check")
    @RequireWorkspaceMember
    public Result<Map<String, Object>> checkBalance(@RequestParam long amount) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        boolean enough = walletService.hasEnoughBalance(workspaceId, amount);
        return Result.success(Map.of(
                "enough", enough,
                "amount", amount
        ));
    }

    // ==================== 交易流水 ====================

    /**
     * 获取交易记录（简单方式，支持向后兼容）
     * GET /wallet/transactions
     */
    @GetMapping("/transactions")
    @RequireWorkspaceMember
    public Result<List<TransactionResponse>> getTransactions(
            @RequestParam(defaultValue = "50") int limit) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<TransactionResponse> transactions = walletService.getTransactions(workspaceId, limit);
        return Result.success(transactions);
    }

    /**
     * 分页获取交易记录
     * GET /wallet/transactions/page
     */
    @GetMapping("/transactions/page")
    @RequireWorkspaceMember
    public Result<PageResult<TransactionResponse>> getTransactionsPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String type) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        PageResult<TransactionResponse> transactions = walletService.getTransactionsPage(workspaceId, current, size, type);
        return Result.success(transactions);
    }

    /**
     * 获取钱包统计
     * GET /wallet/statistics
     */
    @GetMapping("/statistics")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Map<String, Object> statistics = walletService.getStatistics(workspaceId, startDate, endDate);
        return Result.success(statistics);
    }

    // ==================== 配额管理 ====================

    /**
     * 获取我的配额
     * GET /wallet/quotas/my
     */
    @GetMapping("/quotas/my")
    @RequireWorkspaceMember
    public Result<QuotaResponse> getMyQuota() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        QuotaResponse response = quotaService.getOrCreateQuota(workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 获取所有成员配额
     * GET /wallet/quotas
     */
    @GetMapping("/quotas")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<List<QuotaResponse>> listQuotas() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<QuotaResponse> quotas = quotaService.listQuotas(workspaceId);
        return Result.success(quotas);
    }

    /**
     * 设置成员配额
     * PUT /wallet/quotas/{userId}
     */
    @PutMapping("/quotas/{userId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<QuotaResponse> setQuota(
            @PathVariable String userId,
            @RequestBody @Valid QuotaRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        request.setUserId(userId);
        QuotaResponse response = quotaService.setQuota(workspaceId, request, operatorId);
        return Result.success(response);
    }

    /**
     * 重置成员配额
     * POST /wallet/quotas/{userId}/reset
     */
    @PostMapping("/quotas/{userId}/reset")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<QuotaResponse> resetQuota(@PathVariable String userId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        QuotaResponse response = quotaService.resetQuota(workspaceId, userId, operatorId);
        return Result.success(response);
    }

    /**
     * 删除成员配额
     * DELETE /wallet/quotas/{userId}
     */
    @DeleteMapping("/quotas/{userId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> deleteQuota(@PathVariable String userId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        quotaService.deleteQuota(workspaceId, userId, operatorId);
        return Result.success();
    }
}
