package com.actionow.task.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.common.web.sse.SseResponseHelper;
import com.actionow.common.web.sse.SseService;
import com.actionow.task.dto.*;
import com.actionow.task.entity.BatchJob;
import com.actionow.task.entity.BatchJobItem;
import com.actionow.task.mapper.BatchJobItemMapper;
import com.actionow.task.mapper.BatchJobMapper;
import com.actionow.task.service.BatchJobOrchestrator;
import com.actionow.task.service.PipelineEngine;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 批量作业控制器
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/tasks/batch-jobs")
@RequiredArgsConstructor
public class BatchJobController {

    private final BatchJobOrchestrator orchestrator;
    private final PipelineEngine pipelineEngine;
    private final BatchJobMapper batchJobMapper;
    private final BatchJobItemMapper batchJobItemMapper;
    private final SseService sseService;

    /**
     * 创建并启动批量作业
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<BatchJobResponse> create(@RequestBody @Valid CreateBatchJobRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        BatchJobResponse response = orchestrator.createAndStart(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 作用域展开：自动展开指定 episode / script 下的实体并批量生成
     * 便捷入口，自动设置 batchType=SCOPE
     */
    @PostMapping("/expand")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<BatchJobResponse> expand(@RequestBody @Valid CreateBatchJobRequest request) {
        request.setBatchType("SCOPE");
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        BatchJobResponse response = orchestrator.createAndStart(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * A/B 对比：同一实体用不同 Provider 生成，对比效果
     * 便捷入口，自动设置 batchType=AB_TEST
     */
    @PostMapping("/ab-test")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<BatchJobResponse> abTest(@RequestBody @Valid CreateBatchJobRequest request) {
        request.setBatchType("AB_TEST");
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        BatchJobResponse response = orchestrator.createAndStart(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 获取批量作业详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceMember
    public Result<BatchJobResponse> getById(@PathVariable String id) {
        BatchJobResponse response = orchestrator.getById(id);
        return Result.success(response);
    }

    /**
     * 获取批量作业子项列表（分页）
     */
    @GetMapping("/{id}/items")
    @RequireWorkspaceMember
    public Result<PageResult<BatchJobItemResponse>> getItems(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String status) {
        IPage<BatchJobItem> page = batchJobItemMapper.selectPageByBatchJobId(
                new Page<>(pageNum, pageSize), id, status);
        List<BatchJobItemResponse> records = page.getRecords().stream()
                .map(BatchJobItemResponse::fromEntity)
                .toList();
        PageResult<BatchJobItemResponse> result = PageResult.of(
                page.getCurrent(), page.getSize(), page.getTotal(), records);
        return Result.success(result);
    }

    /**
     * 取消批量作业
     */
    @PostMapping("/{id}/cancel")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> cancel(@PathVariable String id) {
        String userId = UserContextHolder.getUserId();
        orchestrator.cancel(id, userId);
        return Result.success();
    }

    /**
     * 暂停批量作业
     */
    @PostMapping("/{id}/pause")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> pause(@PathVariable String id) {
        orchestrator.pause(id);
        return Result.success();
    }

    /**
     * 恢复批量作业
     */
    @PostMapping("/{id}/resume")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> resume(@PathVariable String id) {
        orchestrator.resume(id);
        return Result.success();
    }

    /**
     * 重试所有失败项
     */
    @PostMapping("/{id}/retry-failed")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> retryFailed(@PathVariable String id) {
        orchestrator.retryFailed(id);
        return Result.success();
    }

    /**
     * 重试 Pipeline 指定步骤的失败项
     */
    @PostMapping("/{id}/retry-step/{stepNumber}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> retryPipelineStep(@PathVariable String id,
                                           @PathVariable int stepNumber) {
        pipelineEngine.retryPipelineStep(id, stepNumber);
        return Result.success();
    }

    /**
     * 列表查询（分页）
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<PageResult<BatchJobResponse>> list(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String batchType) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        IPage<BatchJob> page = batchJobMapper.selectPageByWorkspaceId(
                new Page<>(pageNum, pageSize), workspaceId, status, batchType);
        List<BatchJobResponse> records = page.getRecords().stream()
                .map(BatchJobResponse::fromEntity)
                .toList();
        PageResult<BatchJobResponse> result = PageResult.of(
                page.getCurrent(), page.getSize(), page.getTotal(), records);
        return Result.success(result);
    }

    /**
     * SSE 实时进度流
     */
    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireWorkspaceMember
    public SseEmitter progress(@PathVariable String id, HttpServletResponse response) {
        SseResponseHelper.configureSseHeaders(response);
        String userId = UserContextHolder.getUserId();
        String workspaceId = UserContextHolder.getWorkspaceId();
        String connectionId = "batch_" + id + "_" + UuidGenerator.generateUuidV7();

        SseEmitter emitter = sseService.createConnection(
                connectionId, userId, workspaceId, null,
                Map.of("type", "batch_progress", "batchJobId", id));

        // 立即发送当前状态
        BatchJob job = batchJobMapper.selectById(id);
        if (job != null) {
            sseService.sendEvent(connectionId, "progress", BatchJobProgressResponse.builder()
                    .batchJobId(job.getId())
                    .status(job.getStatus())
                    .totalItems(job.getTotalItems())
                    .completedItems(job.getCompletedItems())
                    .failedItems(job.getFailedItems())
                    .skippedItems(job.getSkippedItems())
                    .progressPct(job.getProgress())
                    .actualCredits(job.getActualCredits())
                    .build());
        }

        return emitter;
    }
}
