package com.actionow.task.controller;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.workspace.WorkspaceInternalClient;
import com.actionow.task.dto.CreateTaskRequest;
import com.actionow.task.dto.GenerationTaskResponse;
import com.actionow.task.dto.ProviderExecutionResult;
import com.actionow.task.dto.SubmitGenerationRequest;
import com.actionow.task.dto.TaskResponse;
import com.actionow.task.dto.BatchJobResponse;
import com.actionow.task.dto.CreateBatchJobRequest;
import com.actionow.task.service.AiGenerationFacade;
import com.actionow.task.service.BatchJobOrchestrator;
import com.actionow.task.service.TaskService;
import com.actionow.common.core.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务内部控制器
 * 供其他微服务调用
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/internal/task")
@RequiredArgsConstructor
@IgnoreAuth
public class TaskInternalController {

    private final TaskService taskService;
    private final AiGenerationFacade aiGenerationFacade;
    private final WorkspaceInternalClient workspaceInternalClient;
    private final BatchJobOrchestrator batchJobOrchestrator;

    /**
     * 创建任务（内部调用）
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> createTask(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestBody CreateTaskRequest request) {
        // 内部调用使用 system 作为创建者
        TaskResponse response = taskService.create(request, workspaceId, "system");
        return Result.success(Map.of(
                "taskId", response.getId(),
                "status", response.getStatus()
        ));
    }

    /**
     * 更新任务状态
     */
    @PostMapping("/update-status")
    public Result<Void> updateTaskStatus(@RequestBody UpdateStatusRequest request) {
        if ("RUNNING".equals(request.status())) {
            taskService.startTask(request.taskId());
        } else if ("COMPLETED".equals(request.status())) {
            taskService.completeTask(request.taskId(), request.result());
        } else if ("FAILED".equals(request.status())) {
            taskService.failTask(request.taskId(), request.errorMessage(), request.result());
        }
        return Result.success();
    }

    /**
     * 更新任务进度
     */
    @PostMapping("/update-progress")
    public Result<Void> updateProgress(@RequestBody UpdateProgressRequest request) {
        taskService.updateProgress(request.taskId(), request.progress());
        return Result.success();
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    public Result<TaskResponse> getTask(@PathVariable String taskId) {
        TaskResponse response = taskService.getById(taskId);
        return Result.success(response);
    }

    /**
     * 取消任务（内部调用）
     * Mission 级联取消时调用，best-effort 语义
     */
    @PostMapping("/{taskId}/cancel-internal")
    public Result<Void> cancelTaskInternal(
            @PathVariable String taskId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        try {
            taskService.cancel(taskId, userId);
        } catch (BusinessException e) {
            log.info("取消任务跳过: taskId={}, reason={}", taskId, e.getMessage());
        }
        return Result.success();
    }

    /**
     * 更新状态请求
     */
    public record UpdateStatusRequest(
            String taskId,
            String status,
            String errorMessage,
            Map<String, Object> result
    ) {}

    /**
     * 更新进度请求
     */
    public record UpdateProgressRequest(
            String taskId,
            int progress
    ) {}

    // ==================== AI 生成任务（内部调用） ====================

    /**
     * 提交 AI 生成任务（内部调用）
     * 供 Agent 模块等内部服务调用，走完整的积分冻结 → 任务创建 → MQ 发送流程
     *
     * 注意：Agent 应先创建素材（状态为 GENERATING），再提交任务，将 assetId 传入。
     * 如果 assetId 为空会记录警告，任务完成后可能无法正确更新素材。
     */
    @PostMapping("/ai/generate")
    public Result<Map<String, Object>> submitAiGeneration(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
            @RequestBody @Valid SubmitGenerationRequest request) {
        log.info("内部 AI 生成请求: providerId={}, generationType={}, assetId={}, workspaceId={}",
                request.getProviderId(), request.getGenerationType(), request.getAssetId(), workspaceId);

        // 如果没有 assetId，TEXT 类型是正常的，其他类型记录警告
        if (request.getAssetId() == null || request.getAssetId().isBlank()) {
            if (!"TEXT".equalsIgnoreCase(request.getGenerationType())) {
                log.warn("内部 AI 生成请求缺少 assetId，任务完成后将无法关联到素材。" +
                        "建议 Agent 先创建素材再提交任务。providerId={}", request.getProviderId());
            }
        }

        GenerationTaskResponse response = aiGenerationFacade.submitGeneration(request, workspaceId, userId);
        Map<String, Object> resultMap = new java.util.HashMap<>();
        resultMap.put("taskId", response.getTaskId());
        resultMap.put("status", response.getStatus());
        resultMap.put("assetId", request.getAssetId());
        resultMap.put("providerId", response.getProviderId() != null ? response.getProviderId() : "");
        resultMap.put("creditCost", response.getCreditCost() != null ? response.getCreditCost() : 0);
        return Result.success(resultMap);
    }

    /**
     * 获取任务结果（轮询用）
     */
    @GetMapping("/{taskId}/result")
    public Result<TaskResponse> getTaskResult(@PathVariable String taskId) {
        TaskResponse response = taskService.getById(taskId);
        return Result.success(response);
    }

    // ==================== AI 任务回调 ====================

    /**
     * 接收 AI 任务完成回调
     * 注意：回调来自 AI 服务，没有用户上下文，需要从任务中恢复
     */
    @PostMapping("/{taskId}/callback")
    public Result<Void> handleTaskCallback(
            @PathVariable String taskId,
            @RequestBody @Valid ProviderExecutionResult result) {
        log.info("收到任务回调: taskId={}, success={}", taskId, result.isSuccess());

        // 先获取任务，用于恢复用户上下文
        TaskResponse task = taskService.getById(taskId);
        if (task == null) {
            log.error("任务不存在: taskId={}", taskId);
            return Result.fail("TASK_NOT_FOUND", "任务不存在");
        }

        // 设置用户上下文，确保后续 Feign 调用能正确传递租户信息
        try {
            setContextFromTask(task);
            aiGenerationFacade.handleCompletion(taskId, result);
            return Result.success();
        } finally {
            // 确保清理上下文，防止线程池复用时上下文泄漏
            UserContextHolder.clear();
        }
    }

    /**
     * 从任务恢复用户上下文
     */
    private void setContextFromTask(TaskResponse task) {
        UserContext context = new UserContext();
        context.setWorkspaceId(task.getWorkspaceId());
        context.setUserId(task.getCreatorId());

        // 从 workspace 服务获取正确的租户 Schema
        if (task.getWorkspaceId() != null) {
            try {
                Result<String> schemaResult = workspaceInternalClient.getTenantSchema(task.getWorkspaceId());
                if (schemaResult.isSuccess() && schemaResult.getData() != null) {
                    context.setTenantSchema(schemaResult.getData());
                } else {
                    log.warn("获取租户Schema失败，workspaceId={}", task.getWorkspaceId());
                }
            } catch (Exception e) {
                log.error("获取租户Schema异常: workspaceId={}", task.getWorkspaceId(), e);
            }
        }

        UserContextHolder.setContext(context);
        log.debug("从任务恢复上下文: workspaceId={}, userId={}, tenantSchema={}",
                task.getWorkspaceId(), task.getCreatorId(), context.getTenantSchema());
    }

    // ==================== 批量作业（内部调用） ====================

    /**
     * 创建批量作业（内部调用）
     * 供 Agent 模块调用
     */
    @PostMapping("/batch-jobs")
    public Result<BatchJobResponse> createBatchJob(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
            @RequestBody @Valid CreateBatchJobRequest request) {
        log.info("内部创建批量作业: workspaceId={}, type={}, itemCount={}",
                workspaceId, request.getBatchType(),
                request.getItems() != null ? request.getItems().size() : 0);
        BatchJobResponse response = batchJobOrchestrator.createAndStart(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 获取批量作业状态（内部调用）
     */
    @GetMapping("/batch-jobs/{batchJobId}")
    public Result<BatchJobResponse> getBatchJob(@PathVariable String batchJobId) {
        BatchJobResponse response = batchJobOrchestrator.getById(batchJobId);
        return Result.success(response);
    }

    /**
     * 取消批量作业（内部调用）
     */
    @PostMapping("/batch-jobs/{batchJobId}/cancel")
    public Result<Void> cancelBatchJob(
            @PathVariable String batchJobId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        try {
            batchJobOrchestrator.cancel(batchJobId, userId);
        } catch (BusinessException e) {
            log.info("取消批量作业跳过: batchJobId={}, reason={}", batchJobId, e.getMessage());
        }
        return Result.success();
    }

    /**
     * 获取批量作业子项列表（内部调用）
     */
    @GetMapping("/batch-jobs/{batchJobId}/items")
    public Result<List<Map<String, Object>>> getBatchJobItems(@PathVariable String batchJobId) {
        var items = batchJobOrchestrator.getItems(batchJobId);
        List<Map<String, Object>> result = items.stream().map(item -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", item.getId());
            map.put("sequenceNumber", item.getSequenceNumber());
            map.put("entityType", item.getEntityType());
            map.put("entityId", item.getEntityId());
            map.put("entityName", item.getEntityName());
            map.put("status", item.getStatus());
            map.put("taskId", item.getTaskId());
            map.put("assetId", item.getAssetId());
            map.put("errorMessage", item.getErrorMessage());
            map.put("variantIndex", item.getVariantIndex());
            map.put("skipped", item.getSkipped());
            map.put("skipReason", item.getSkipReason());
            map.put("creditCost", item.getCreditCost());
            return map;
        }).toList();
        return Result.success(result);
    }
}
