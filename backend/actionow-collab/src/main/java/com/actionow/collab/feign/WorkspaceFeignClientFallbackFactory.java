package com.actionow.collab.feign;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Workspace Feign 客户端降级工厂
 * 降级时拒绝连接（fail-closed），防止 workspace 服务不可用时出现越权访问
 *
 * @author Actionow
 */
@Slf4j
@Component
public class WorkspaceFeignClientFallbackFactory implements FallbackFactory<WorkspaceFeignClient> {

    @Override
    public WorkspaceFeignClient create(Throwable cause) {
        log.error("调用 Workspace 服务失败，WebSocket 握手将拒绝: {}", cause.getMessage());
        return (workspaceId, userId) -> {
            log.warn("Workspace 服务不可用，拒绝 userId={} 连接 workspaceId={}", userId, workspaceId);
            // fail-closed：服务不可用时拒绝握手，防止越权
            return Result.fail("503", "Workspace 服务不可用");
        };
    }
}
