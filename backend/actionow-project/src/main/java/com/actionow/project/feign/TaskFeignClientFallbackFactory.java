package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class TaskFeignClientFallbackFactory implements FallbackFactory<TaskFeignClient> {

    @Override
    public TaskFeignClient create(Throwable cause) {
        log.error("调用任务服务失败: {}", cause.getMessage());
        return new TaskFeignClient() {
            @Override
            public Result<Map<String, Object>> submitAiGeneration(
                    String workspaceId, String userId, Map<String, Object> request) {
                log.warn("AI生成任务提交降级: workspaceId={}", workspaceId);
                return Result.fail("50001", "任务服务暂时不可用");
            }
        };
    }
}
