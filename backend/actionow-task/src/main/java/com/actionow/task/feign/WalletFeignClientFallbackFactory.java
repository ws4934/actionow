package com.actionow.task.feign;

import com.actionow.common.core.result.Result;
import com.actionow.task.dto.FreezeRequest;
import com.actionow.task.dto.FreezeResponse;
import com.actionow.task.dto.ConfirmConsumeRequest;
import com.actionow.task.dto.UnfreezeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 钱包服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class WalletFeignClientFallbackFactory implements FallbackFactory<WalletFeignClient> {

    @Override
    public WalletFeignClient create(Throwable cause) {
        log.error("调用钱包服务失败: {}", cause.getMessage());
        return new WalletFeignClient() {
            @Override
            public Result<FreezeResponse> freeze(String workspaceId, FreezeRequest request) {
                log.warn("冻结积分降级: workspaceId={}, amount={}", workspaceId, request.getAmount());
                return Result.fail("50003", "钱包服务暂时不可用");
            }

            @Override
            public Result<Void> confirmConsume(String workspaceId, ConfirmConsumeRequest request) {
                log.warn("确认消费降级: workspaceId={}, businessId={}", workspaceId, request.getBusinessId());
                return Result.fail("50003", "钱包服务暂时不可用，消费确认将稍后重试");
            }

            @Override
            public Result<Void> unfreeze(String workspaceId, UnfreezeRequest request) {
                log.warn("解冻积分降级: workspaceId={}, businessId={}", workspaceId, request.getBusinessId());
                return Result.fail("50003", "钱包服务暂时不可用，解冻将稍后重试");
            }

            @Override
            public Result<Boolean> checkQuota(String workspaceId, String userId, long amount) {
                log.warn("检查配额降级: workspaceId={}, userId={}, amount={}", workspaceId, userId, amount);
                // 不可 fail-open：钱包不可用时必须由调用方显式决定是否放行
                return Result.fail("50003", "钱包服务暂时不可用，无法校验配额");
            }

            @Override
            public Result<Boolean> useQuota(String workspaceId, String userId, long amount) {
                log.warn("使用配额降级: workspaceId={}, userId={}, amount={}", workspaceId, userId, amount);
                // 不可 fail-open：返回成功会让上层误以为配额已占用，后续实际消费将超额
                return Result.fail("50003", "钱包服务暂时不可用，配额占用失败");
            }

            @Override
            public Result<Boolean> refundQuota(String workspaceId, String userId, long amount) {
                log.warn("退还配额降级: workspaceId={}, userId={}, amount={}", workspaceId, userId, amount);
                // 不可 fail-open：返回成功会让补偿链误 markCompleted 导致丢失退款
                return Result.fail("50003", "钱包服务暂时不可用，配额退还失败");
            }
        };
    }
}
