package com.actionow.ai.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 任务服务 Feign 客户端
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-task", path = "/internal/task")
public interface TaskFeignClient {

    /**
     * 创建任务
     */
    @PostMapping("/create")
    Result<Map<String, Object>> createTask(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody CreateTaskRequest request);

    /**
     * 更新任务状态
     */
    @PostMapping("/update-status")
    Result<Void> updateTaskStatus(@RequestBody UpdateTaskStatusRequest request);

    /**
     * 通知 Task 模块处理 AI 回调结果
     * 用于 CALLBACK 模式：第三方回调到达 AI 模块后，转发给 Task 模块触发完成流程
     *
     * @param taskId  任务 ID
     * @param payload 符合 ProviderExecutionResult 结构的回调数据（由 PluginExecutionResult.toCallbackPayload() 生成）
     */
    @PostMapping("/{taskId}/callback")
    Result<Void> notifyTaskCallback(
            @PathVariable("taskId") String taskId,
            @RequestBody Map<String, Object> payload);

    /**
     * 创建任务请求
     */
    record CreateTaskRequest(
            String name,
            String type,
            String priority,
            Map<String, Object> inputParams,
            String callbackUrl,
            String creatorId
    ) {}

    /**
     * 更新任务状态请求
     */
    record UpdateTaskStatusRequest(
            String taskId,
            String status,
            String errorMessage,
            Map<String, Object> result
    ) {}
}
