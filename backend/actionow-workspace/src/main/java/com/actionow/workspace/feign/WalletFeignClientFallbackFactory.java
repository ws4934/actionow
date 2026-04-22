package com.actionow.workspace.feign;

import com.actionow.common.core.result.Result;
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
            public Result<WalletBasicInfo> createWallet(String workspaceId) {
                log.warn("创建钱包降级: workspaceId={}", workspaceId);
                return Result.fail("50003", "钱包服务暂时不可用，钱包创建失败");
            }

            @Override
            public Result<Void> deleteQuota(String workspaceId, String userId, String operatorId) {
                log.warn("删除成员配额降级: workspaceId={}, userId={}", workspaceId, userId);
                return Result.fail("50003", "钱包服务暂时不可用，配额清理失败");
            }
        };
    }
}
