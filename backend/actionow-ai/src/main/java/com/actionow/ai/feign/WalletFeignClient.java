package com.actionow.ai.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 钱包服务 Feign 客户端
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-wallet", path = "/internal/wallet")
public interface WalletFeignClient {

    /**
     * 冻结积分
     */
    @PostMapping("/freeze")
    Result<Map<String, Object>> freeze(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody FreezeRequest request);

    /**
     * 解冻积分
     */
    @PostMapping("/unfreeze")
    Result<Void> unfreeze(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody UnfreezeRequest request);

    /**
     * 确认消费
     */
    @PostMapping("/confirm-consume")
    Result<Void> confirmConsume(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody ConfirmConsumeRequest request);

    /**
     * 冻结请求
     */
    record FreezeRequest(
            Long amount,
            String scene,
            String businessId,
            String remark
    ) {}

    /**
     * 解冻请求
     */
    record UnfreezeRequest(
            String transactionId,
            String businessId,
            String remark
    ) {}

    /**
     * 确认消费请求
     */
    record ConfirmConsumeRequest(
            String transactionId,
            String businessId,
            Long actualAmount,
            String remark
    ) {}
}
