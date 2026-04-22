package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.workspace.WorkspaceMembershipInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 工作空间服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class WorkspaceFeignClientFallbackFactory implements FallbackFactory<WorkspaceFeignClient> {

    @Override
    public WorkspaceFeignClient create(Throwable cause) {
        log.error("调用工作空间服务失败: {}", cause.getMessage());
        return new WorkspaceFeignClient() {
            @Override
            public Result<WorkspaceMembershipInfo> getMembership(String workspaceId, String userId) {
                log.warn("获取成员身份信息降级: workspaceId={}, userId={}", workspaceId, userId);
                return Result.success(WorkspaceMembershipInfo.builder()
                        .workspaceId(workspaceId)
                        .userId(userId)
                        .member(false)
                        .build());
            }

            @Override
            public Result<Void> addGuestMember(String workspaceId, String userId, String invitedBy) {
                log.warn("添加 Guest 成员降级（失败）: workspaceId={}, userId={}", workspaceId, userId);
                return Result.fail("工作空间服务不可用");
            }
        };
    }
}
