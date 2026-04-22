package com.actionow.task.feign;

import com.actionow.common.core.result.Result;
import com.actionow.task.dto.FreezeRequest;
import com.actionow.task.dto.FreezeResponse;
import com.actionow.task.dto.ConfirmConsumeRequest;
import com.actionow.task.dto.UnfreezeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 钱包服务 Feign 客户端
 * Task 服务调用 Wallet 服务管理积分
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-wallet", path = "/internal/wallet",
        fallbackFactory = WalletFeignClientFallbackFactory.class)
public interface WalletFeignClient {

    /**
     * 冻结积分
     *
     * @param workspaceId 工作空间 ID
     * @param request 冻结请求
     * @return 冻结结果（含 transactionId）
     */
    @PostMapping("/freeze")
    Result<FreezeResponse> freeze(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody FreezeRequest request);

    /**
     * 确认消费（扣除冻结的积分）
     *
     * @param workspaceId 工作空间 ID
     * @param request 确认消费请求
     * @return 操作结果
     */
    @PostMapping("/confirm")
    Result<Void> confirmConsume(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody ConfirmConsumeRequest request);

    /**
     * 解冻积分（取消冻结）
     *
     * @param workspaceId 工作空间 ID
     * @param request 解冻请求
     * @return 操作结果
     */
    @PostMapping("/unfreeze")
    Result<Void> unfreeze(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody UnfreezeRequest request);

    // ==================== 成员配额相关 ====================

    /**
     * 检查成员配额是否足够
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param amount      预计使用的积分数量
     * @return 配额是否足够
     */
    @GetMapping("/{workspaceId}/quota/check")
    Result<Boolean> checkQuota(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("userId") String userId,
            @RequestParam("amount") long amount);

    /**
     * 使用成员配额
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param amount      使用的积分数量
     * @return 是否成功
     */
    @PostMapping("/{workspaceId}/quota/use")
    Result<Boolean> useQuota(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("userId") String userId,
            @RequestParam("amount") long amount);

    /**
     * 退还成员配额
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param amount      退还的积分数量
     * @return 是否成功
     */
    @PostMapping("/{workspaceId}/quota/refund")
    Result<Boolean> refundQuota(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("userId") String userId,
            @RequestParam("amount") long amount);
}
