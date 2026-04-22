package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 任务服务 Feign 客户端
 * 用于灵感模块提交 AI 生成任务
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-task", path = "/internal/task",
        fallbackFactory = TaskFeignClientFallbackFactory.class)
public interface TaskFeignClient {

    /**
     * 提交 AI 生成任务
     * 走完整流程：积分冻结 → 任务创建 → MQ 发送 → AI 执行
     */
    @PostMapping("/ai/generate")
    Result<Map<String, Object>> submitAiGeneration(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> request);
}
