package com.actionow.task.controller;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.common.web.sse.SseResponseHelper;
import com.actionow.common.web.sse.SseService;
import com.actionow.task.dto.*;
import com.actionow.task.service.AiGenerationFacade;
import com.actionow.task.service.StreamGenerationService;
import com.actionow.task.service.TaskService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 任务控制器
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final AiGenerationFacade aiGenerationFacade;
    private final StreamGenerationService streamGenerationService;
    private final SseService sseService;

    // Virtual thread executor for SSE streaming
    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建任务
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<TaskResponse> create(@RequestBody @Valid CreateTaskRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        TaskResponse response = taskService.create(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 取消任务
     */
    @PostMapping("/{taskId}/cancel")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> cancel(@PathVariable String taskId) {
        String userId = UserContextHolder.getUserId();
        taskService.cancel(taskId, userId);
        return Result.success();
    }

    /**
     * 重试任务
     */
    @PostMapping("/{taskId}/retry")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<TaskResponse> retry(@PathVariable String taskId) {
        String userId = UserContextHolder.getUserId();
        TaskResponse response = taskService.retry(taskId, userId);
        return Result.success(response);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    @RequireWorkspaceMember
    public Result<TaskResponse> getById(@PathVariable String taskId) {
        TaskResponse response = taskService.getById(taskId);
        return Result.success(response);
    }

    /**
     * 分页获取任务列表
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页大小
     * @param status   状态（可选）
     * @param type     类型（可选）
     * @return 分页任务列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<PageResult<TaskResponse>> listPage(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scriptId,
            @RequestParam(required = false) String entityType) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        PageResult<TaskResponse> pageResult = taskService.listByWorkspacePage(
                workspaceId, pageNum, pageSize, status, type, scriptId, entityType);
        return Result.success(pageResult);
    }

    /**
     * 获取任务列表（全量，无分页）
     *
     * @param status 状态（可选）
     * @return 任务列表
     */
    @GetMapping("/all")
    @RequireWorkspaceMember
    public Result<List<TaskResponse>> listAll(@RequestParam(required = false) String status) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<TaskResponse> tasks;
        if (status != null && !status.isEmpty()) {
            tasks = taskService.listByWorkspaceAndStatus(workspaceId, status);
        } else {
            tasks = taskService.listByWorkspace(workspaceId);
        }
        return Result.success(tasks);
    }

    /**
     * 分页获取我创建的任务
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页大小
     * @param status   状态（可选）
     * @return 分页任务列表
     */
    @GetMapping("/my")
    @RequireWorkspaceMember
    public Result<PageResult<TaskResponse>> listMyTasksPage(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String status) {
        String userId = UserContextHolder.getUserId();
        PageResult<TaskResponse> pageResult = taskService.listByCreatorPage(userId, pageNum, pageSize, status);
        return Result.success(pageResult);
    }

    /**
     * 获取我创建的任务（全量，无分页）
     */
    @GetMapping("/my/all")
    @RequireWorkspaceMember
    public Result<List<TaskResponse>> listMyTasksAll() {
        String userId = UserContextHolder.getUserId();
        List<TaskResponse> tasks = taskService.listByCreator(userId);
        return Result.success(tasks);
    }

    /**
     * 获取运行中任务数
     */
    @GetMapping("/stats/running")
    @RequireWorkspaceMember
    public Result<Map<String, Object>> getRunningCount() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        int count = taskService.getRunningTaskCount(workspaceId);
        return Result.success(Map.of("count", count));
    }

    // ==================== AI 生成任务相关端点 ====================

    /**
     * 提交 AI 生成任务
     */
    @PostMapping("/ai/generate")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<GenerationTaskResponse> submitAiGeneration(@RequestBody @Valid SubmitGenerationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        GenerationTaskResponse response = aiGenerationFacade.submitGeneration(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 获取可用的 AI 模型提供商列表
     */
    @GetMapping("/ai/providers")
    @RequireWorkspaceMember
    public Result<List<AvailableProviderResponse>> getAvailableProviders(
            @RequestParam(required = false, defaultValue = "IMAGE") String providerType) {
        List<AvailableProviderResponse> providers = aiGenerationFacade.getAvailableProviders(providerType);
        return Result.success(providers);
    }

    /**
     * 预估积分消耗
     */
    @PostMapping("/ai/estimate-cost")
    @RequireWorkspaceMember
    public Result<Map<String, Object>> estimateCost(
            @RequestParam String providerId,
            @RequestBody(required = false) Map<String, Object> params) {
        Map<String, Object> estimate = aiGenerationFacade.estimateCost(providerId, params);
        return Result.success(estimate);
    }

    /**
     * 取消 AI 生成任务
     */
    @PostMapping("/{taskId}/cancel-ai")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> cancelAiGeneration(@PathVariable String taskId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        aiGenerationFacade.cancelGeneration(taskId, workspaceId, userId);
        return Result.success();
    }

    // ==================== 流式生成 API ====================

    /**
     * 流式 AI 生成任务
     * 支持实时返回生成进度和结果
     *
     * 使用统一 SseService 实现标准的流式输出
     */
    @PostMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public SseEmitter streamAiGeneration(
            @RequestBody @Valid StreamGenerationRequest request,
            HttpServletResponse response) {
        // 设置标准 SSE 响应头
        SseResponseHelper.configureSseHeaders(response);

        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        UserContext capturedContext = UserContextHolder.getContext();

        // 生成连接ID
        String connectionId = "stream_" + UuidGenerator.generateUuidV7();

        log.info("Stream generation request: connectionId={}, providerId={}, workspaceId={}",
                connectionId, request.getProviderId(), workspaceId);

        // 使用 SseService 创建连接
        SseEmitter emitter = sseService.createConnection(
                connectionId,
                userId,
                workspaceId,
                null,
                Map.of("type", "ai_stream", "providerId", request.getProviderId())
        );

        // 在 Virtual Thread 中执行流式处理
        sseExecutor.submit(() -> {
            try {
                // 恢复用户上下文
                if (capturedContext != null) {
                    UserContextHolder.setContext(capturedContext);
                }

                log.info("SSE stream started: connectionId={}", connectionId);

                // 订阅流式生成服务并发送事件
                streamGenerationService.streamGenerate(request, workspaceId, userId)
                        .doOnNext(event -> {
                            // 使用 SseService 发送事件（带标准SSE格式）
                            boolean sent = sseService.sendEvent(
                                    connectionId,
                                    event.getEventType().toLowerCase(),
                                    event
                            );
                            if (sent) {
                                log.debug("SSE event sent: connectionId={}, eventType={}",
                                        connectionId, event.getEventType());
                            }
                        })
                        .doOnComplete(() -> {
                            // 使用 SseService 完成连接
                            sseService.complete(connectionId);
                            log.info("SSE stream completed: connectionId={}", connectionId);
                        })
                        .doOnError(e -> {
                            log.error("SSE stream error: connectionId={}", connectionId, e);
                            sseService.completeWithError(connectionId, e);
                        })
                        .blockLast(); // 在 Virtual Thread 中阻塞等待完成
            } catch (Exception e) {
                log.error("SSE executor error: connectionId={}", connectionId, e);
                sseService.completeWithError(connectionId, e);
            } finally {
                UserContextHolder.clear();
            }
        });

        return emitter;
    }

    // ==================== 批量任务 API ====================

    /**
     * 批量提交 AI 生成任务
     */
    @PostMapping("/ai/batch")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<BatchGenerationResponse> submitBatchGeneration(
            @RequestBody @Valid BatchGenerationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        BatchGenerationResponse response = aiGenerationFacade.submitBatchGeneration(request, workspaceId, userId);
        return Result.success(response);
    }

    // ==================== 优先级 API ====================

    /**
     * 调整任务优先级
     *
     * @param taskId   任务ID
     * @param priority 新优先级 (1-5, 1最高)
     */
    @PostMapping("/{taskId}/priority")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> adjustPriority(
            @PathVariable String taskId,
            @RequestParam int priority) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        aiGenerationFacade.adjustTaskPriority(taskId, workspaceId, userId, priority);
        return Result.success();
    }

    /**
     * 获取任务队列位置
     */
    @GetMapping("/{taskId}/queue-position")
    @RequireWorkspaceMember
    public Result<Map<String, Object>> getQueuePosition(@PathVariable String taskId) {
        int position = aiGenerationFacade.getQueuePosition(taskId);
        return Result.success(Map.of(
                "taskId", taskId,
                "position", position,
                "inQueue", position >= 0
        ));
    }

    // ==================== 实体生成 API ====================

    /**
     * 提交实体生成任务
     * 一体化接口：自动创建 Asset → 创建关联 → 提交任务
     */
    @PostMapping("/entity-generation")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityGenerationResponse> submitEntityGeneration(
            @RequestBody @Valid EntityGenerationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        EntityGenerationResponse response = aiGenerationFacade.submitEntityGeneration(request, workspaceId, userId);
        // 根据响应状态返回成功或失败
        if (response == null || !response.isSuccess()) {
            String errorMsg = response != null ? response.getErrorMessage() : "提交任务失败";
            return Result.fail(ResultCode.INTERNAL_ERROR, errorMsg);
        }
        return Result.success(response);
    }

    /**
     * 批量提交实体生成任务
     */
    @PostMapping("/entity-generation/batch")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<BatchEntityGenerationResponse> submitBatchEntityGeneration(
            @RequestBody @Valid BatchEntityGenerationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        BatchEntityGenerationResponse response = aiGenerationFacade.submitBatchEntityGeneration(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 重试生成任务
     */
    @PostMapping("/entity-generation/retry")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityGenerationResponse> retryGeneration(
            @RequestBody @Valid RetryGenerationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        EntityGenerationResponse response = aiGenerationFacade.retryGeneration(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 查询生成状态
     */
    @GetMapping("/entity-generation/{assetId}/status")
    @RequireWorkspaceMember
    public Result<Map<String, Object>> getGenerationStatus(@PathVariable String assetId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Map<String, Object> status = aiGenerationFacade.getGenerationStatus(assetId, workspaceId);
        return Result.success(status);
    }
}
