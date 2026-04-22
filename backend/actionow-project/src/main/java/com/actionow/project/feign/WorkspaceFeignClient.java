package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.workspace.WorkspaceMembershipInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工作空间服务 Feign 客户端
 * 供 project 服务调用 workspace 内部接口
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-workspace", path = "/internal/workspace",
        fallbackFactory = WorkspaceFeignClientFallbackFactory.class)
public interface WorkspaceFeignClient {

    /**
     * 获取用户在工作空间的成员身份信息
     *
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 成员身份信息
     */
    @GetMapping("/{workspaceId}/membership")
    Result<WorkspaceMembershipInfo> getMembership(@PathVariable("workspaceId") String workspaceId,
                                                   @RequestParam("userId") String userId);

    /**
     * 将用户以 GUEST 角色添加为工作空间成员（供剧本创建者邀请非成员时使用）
     *
     * @param workspaceId 工作空间ID
     * @param userId      被邀请用户ID
     * @param invitedBy   邀请人ID
     * @return 操作结果
     */
    @PostMapping("/{workspaceId}/members/guest")
    Result<Void> addGuestMember(@PathVariable("workspaceId") String workspaceId,
                                @RequestParam("userId") String userId,
                                @RequestParam("invitedBy") String invitedBy);
}
