package com.actionow.wallet.controller;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.wallet.dto.*;
import com.actionow.wallet.service.QuotaService;
import com.actionow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 钱包内部接口
 * 供其他微服务调用，不对外暴露
 * API路径: /internal/wallet
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/internal/wallet")
@RequiredArgsConstructor
@IgnoreAuth
public class WalletInternalController {

    private final WalletService walletService;
    private final QuotaService quotaService;

    /**
     * 创建工作空间钱包
     * 在创建工作空间时调用，确保钱包与工作空间同时创建
     * POST /internal/wallet/{workspaceId}/create
     */
    @PostMapping("/{workspaceId}/create")
    public Result<WalletResponse> createWallet(@PathVariable String workspaceId) {
        WalletResponse response = walletService.getOrCreateWallet(workspaceId);
        return Result.success(response);
    }

    /**
     * 支付成功后执行充值入账
     * POST /internal/wallet/{workspaceId}/topup
     */
    @PostMapping("/{workspaceId}/topup")
    public Result<TransactionResponse> topup(
            @PathVariable String workspaceId,
            @RequestBody TopupRequest request,
            @RequestParam(defaultValue = "billing-system") String operatorId) {
        TransactionResponse response = walletService.topup(workspaceId, request, operatorId);
        return Result.success(response);
    }

    /**
     * 检查余额是否足够
     * GET /internal/wallet/{workspaceId}/check
     */
    @GetMapping("/{workspaceId}/check")
    public Result<Boolean> checkBalance(
            @PathVariable String workspaceId,
            @RequestParam long amount) {
        boolean enough = walletService.hasEnoughBalance(workspaceId, amount);
        return Result.success(enough);
    }

    /**
     * 检查配额是否足够
     * GET /internal/wallet/{workspaceId}/quota/check
     */
    @GetMapping("/{workspaceId}/quota/check")
    public Result<Boolean> checkQuota(
            @PathVariable String workspaceId,
            @RequestParam String userId,
            @RequestParam long amount) {
        boolean enough = quotaService.hasEnoughQuota(workspaceId, userId, amount);
        return Result.success(enough);
    }

    /**
     * 冻结金额（任务提交前调用）
     * POST /internal/wallet/freeze
     */
    @PostMapping("/freeze")
    public Result<TransactionResponse> freeze(
            @RequestBody FreezeRequest request) {
        TransactionResponse response = walletService.freeze(
                request.getWorkspaceId(), request, request.getOperatorId());
        return Result.success(response);
    }

    /**
     * 解冻金额（任务取消时调用）
     * POST /internal/wallet/unfreeze
     */
    @PostMapping("/unfreeze")
    public Result<TransactionResponse> unfreeze(
            @RequestBody UnfreezeRequest request) {
        TransactionResponse response = walletService.unfreeze(
                request.getWorkspaceId(), request.getBusinessId(),
                request.getBusinessType(), request.getOperatorId());
        return Result.success(response);
    }

    /**
     * 确认消费（任务完成时调用）
     * POST /internal/wallet/confirm
     */
    @PostMapping("/confirm")
    public Result<TransactionResponse> confirmConsume(
            @RequestBody ConfirmConsumeRequest request) {
        TransactionResponse response = walletService.confirmConsume(
                request.getWorkspaceId(), request.getBusinessId(),
                request.getBusinessType(), request.getActualAmount(),
                request.getOperatorId());
        return Result.success(response);
    }

    /**
     * 使用配额
     * POST /internal/wallet/{workspaceId}/quota/use
     */
    @PostMapping("/{workspaceId}/quota/use")
    public Result<Boolean> useQuota(
            @PathVariable String workspaceId,
            @RequestParam String userId,
            @RequestParam long amount) {
        boolean success = quotaService.useQuota(workspaceId, userId, amount);
        return Result.success(success);
    }

    /**
     * 退还配额
     * POST /internal/wallet/{workspaceId}/quota/refund
     */
    @PostMapping("/{workspaceId}/quota/refund")
    public Result<Boolean> refundQuota(
            @PathVariable String workspaceId,
            @RequestParam String userId,
            @RequestParam long amount) {
        boolean success = quotaService.refundQuota(workspaceId, userId, amount);
        return Result.success(success);
    }

    /**
     * 删除成员配额（成员移除/退出时调用）
     * DELETE /internal/wallet/{workspaceId}/quota
     */
    @DeleteMapping("/{workspaceId}/quota")
    public Result<Void> deleteQuota(
            @PathVariable String workspaceId,
            @RequestParam String userId,
            @RequestParam String operatorId) {
        quotaService.deleteQuota(workspaceId, userId, operatorId);
        return Result.success();
    }

    /**
     * 关闭工作空间钱包（Workspace 解散时调用）
     * POST /internal/wallet/{workspaceId}/close
     */
    @PostMapping("/{workspaceId}/close")
    public Result<Void> closeWallet(
            @PathVariable String workspaceId,
            @RequestParam String operatorId) {
        walletService.closeWallet(workspaceId, operatorId);
        return Result.success();
    }

    /**
     * 计划变更时批量调整成员配额上限
     * POST /internal/wallet/{workspaceId}/quota/adjust-plan
     */
    @PostMapping("/{workspaceId}/quota/adjust-plan")
    public Result<Void> adjustQuotasForPlan(
            @PathVariable String workspaceId,
            @RequestParam String planType) {
        quotaService.adjustQuotasForPlan(workspaceId, planType);
        return Result.success();
    }
}
