package com.actionow.workspace.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 钱包服务 Feign 客户端
 * Workspace 服务在创建工作空间时调用，确保同时创建钱包
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-wallet", path = "/internal/wallet",
        fallbackFactory = WalletFeignClientFallbackFactory.class)
public interface WalletFeignClient {

    /**
     * 创建工作空间钱包
     * 在创建工作空间时调用，确保钱包与工作空间同时创建
     *
     * @param workspaceId 工作空间 ID
     * @return 创建结果
     */
    @PostMapping("/{workspaceId}/create")
    Result<WalletBasicInfo> createWallet(@PathVariable("workspaceId") String workspaceId);

    /**
     * 删除成员配额记录
     * 在移除/退出成员时调用，避免配额幽灵数据
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param operatorId  操作人 ID
     * @return 操作结果
     */
    @DeleteMapping("/{workspaceId}/quota")
    Result<Void> deleteQuota(@PathVariable("workspaceId") String workspaceId,
                             @RequestParam("userId") String userId,
                             @RequestParam("operatorId") String operatorId);
}
