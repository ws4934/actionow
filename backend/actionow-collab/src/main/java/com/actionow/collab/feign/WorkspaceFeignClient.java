package com.actionow.collab.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工作空间服务 Feign 客户端
 * 用于 WebSocket 握手阶段验证用户是否为 workspace 成员
 *
 * @author Actionow
 */
@FeignClient(
        name = "actionow-workspace",
        path = "/internal/workspace",
        fallbackFactory = WorkspaceFeignClientFallbackFactory.class
)
public interface WorkspaceFeignClient {

    /**
     * 验证用户是否是工作空间成员
     *
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 成员身份信息
     */
    @GetMapping("/{workspaceId}/membership")
    Result<WorkspaceMembershipInfo> getMembership(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("userId") String userId);
}
