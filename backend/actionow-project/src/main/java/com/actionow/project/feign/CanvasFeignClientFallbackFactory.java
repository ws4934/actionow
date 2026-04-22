package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Canvas Feign 客户端降级工厂
 *
 * 统一主画布模型：1 Script = 1 Canvas
 *
 * @author Actionow
 */
@Slf4j
@Component
public class CanvasFeignClientFallbackFactory implements FallbackFactory<CanvasFeignClient> {

    @Override
    public CanvasFeignClient create(Throwable cause) {
        log.error("调用Canvas服务失败: {}", cause.getMessage());

        return new CanvasFeignClient() {
            @Override
            public Result<Map<String, Object>> initScriptCanvas(String scriptId, String workspaceId, String scriptName) {
                log.warn("初始化剧本画布降级: scriptId={}, workspaceId={}", scriptId, workspaceId);
                return Result.fail("Canvas服务不可用");
            }

            @Override
            public Result<Map<String, Object>> getOrCreateByScriptId(String scriptId, String workspaceId) {
                log.warn("获取或创建剧本画布降级: scriptId={}, workspaceId={}", scriptId, workspaceId);
                return Result.fail("Canvas服务不可用");
            }

            @Override
            public Result<Boolean> existsByScriptId(String scriptId) {
                log.warn("检查剧本画布是否存在降级: scriptId={}", scriptId);
                return Result.fail("Canvas服务不可用");
            }

            @Override
            public Result<Void> deleteByScriptId(String scriptId, String userId) {
                log.warn("删除剧本画布降级: scriptId={}, userId={}", scriptId, userId);
                return Result.fail("Canvas服务不可用");
            }

            @Override
            public Result<Map<String, Object>> createNode(Map<String, Object> request, String workspaceId) {
                log.warn("创建画布节点降级: workspaceId={}", workspaceId);
                return Result.fail("Canvas服务不可用");
            }
        };
    }
}
