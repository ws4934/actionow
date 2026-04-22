package com.actionow.task.service;

import com.actionow.common.web.sse.SseService;
import com.actionow.task.dto.BatchJobProgressResponse;
import com.actionow.task.entity.BatchJob;
import com.actionow.task.entity.BatchJobItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 批量作业 SSE 进度推送服务
 * <p>
 * <b>SSE 架构说明（三种 SSE 的职责划分）：</b>
 * <pre>
 * 1. BatchJobSseService（本类）
 *    模式：workspace 广播（SseService.broadcastToWorkspace）
 *    场景：BatchJob 进度更新，多个客户端可能同时关注同一 workspace 的 Batch 任务
 *    特点：一对多推送，基于 workspaceId 路由
 *
 * 2. StreamGenerationServiceImpl
 *    模式：每请求独立 WebFlux Flux 流（WebClient → AI 服务 /execute/stream）
 *    场景：单次实时流式生成（LLM token 逐字输出、图片生成进度）
 *    特点：一对一流，客户端直接消费 AI 服务的 SSE 流，不经 MQ
 *
 * 3. MissionSseService（agent 模块）
 *    模式：workspace 广播（同 BatchJobSseService）
 *    场景：Mission 执行步骤实时进度
 *    特点：一对多推送
 * </pre>
 * 三者共用底层 {@code SseService.broadcastToWorkspace} 统一管理连接生命周期（超时、断开、重连），
 * 业务层只注入自己的事件类型，无需重复实现连接管理。
 * StreamGenerationServiceImpl 使用不同机制（WebFlux Reactive），因其需要直接转发 AI 服务的流式响应，
 * 属于有意为之的差异设计，不需要统一。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobSseService {

    private final SseService sseService;

    public void sendBatchStarted(BatchJob job) {
        broadcastToWorkspace(job.getWorkspaceId(), "batch_started", Map.of(
                "batchJobId", job.getId(),
                "totalItems", job.getTotalItems(),
                "status", job.getStatus()
        ));
    }

    public void sendItemSubmitted(String workspaceId, BatchJobItem item) {
        broadcastToWorkspace(workspaceId, "item_submitted", Map.of(
                "batchJobId", item.getBatchJobId(),
                "itemId", item.getId(),
                "taskId", item.getTaskId() != null ? item.getTaskId() : "",
                "sequenceNumber", item.getSequenceNumber()
        ));
    }

    public void sendItemCompleted(String workspaceId, BatchJobItem item) {
        broadcastToWorkspace(workspaceId, "item_completed", Map.of(
                "batchJobId", item.getBatchJobId(),
                "itemId", item.getId(),
                "taskId", item.getTaskId() != null ? item.getTaskId() : "",
                "creditCost", item.getCreditCost() != null ? item.getCreditCost() : 0
        ));
    }

    public void sendItemFailed(String workspaceId, BatchJobItem item) {
        broadcastToWorkspace(workspaceId, "item_failed", Map.of(
                "batchJobId", item.getBatchJobId(),
                "itemId", item.getId(),
                "taskId", item.getTaskId() != null ? item.getTaskId() : "",
                "errorMessage", item.getErrorMessage() != null ? item.getErrorMessage() : ""
        ));
    }

    public void sendItemSkipped(String workspaceId, BatchJobItem item) {
        broadcastToWorkspace(workspaceId, "item_skipped", Map.of(
                "batchJobId", item.getBatchJobId(),
                "itemId", item.getId(),
                "reason", item.getSkipReason() != null ? item.getSkipReason() : ""
        ));
    }

    public void sendProgress(BatchJob job) {
        BatchJobProgressResponse progress = BatchJobProgressResponse.builder()
                .batchJobId(job.getId())
                .status(job.getStatus())
                .totalItems(job.getTotalItems())
                .completedItems(job.getCompletedItems())
                .failedItems(job.getFailedItems())
                .skippedItems(job.getSkippedItems())
                .progressPct(job.getProgress())
                .actualCredits(job.getActualCredits())
                .build();
        broadcastToWorkspace(job.getWorkspaceId(), "progress", progress);
    }

    public void sendBatchCompleted(BatchJob job) {
        broadcastToWorkspace(job.getWorkspaceId(), "batch_completed", Map.of(
                "batchJobId", job.getId(),
                "actualCredits", job.getActualCredits() != null ? job.getActualCredits() : 0,
                "completedItems", job.getCompletedItems(),
                "failedItems", job.getFailedItems(),
                "skippedItems", job.getSkippedItems()
        ));
    }

    public void sendBatchFailed(BatchJob job, String errorMessage) {
        broadcastToWorkspace(job.getWorkspaceId(), "batch_failed", Map.of(
                "batchJobId", job.getId(),
                "errorMessage", errorMessage != null ? errorMessage : ""
        ));
    }

    public void sendStepCompleted(BatchJob job, int stepNumber, String stepName,
                                   int completed, int failed) {
        broadcastToWorkspace(job.getWorkspaceId(), "step_completed", Map.of(
                "batchJobId", job.getId(),
                "stepNumber", stepNumber,
                "stepName", stepName != null ? stepName : "",
                "completed", completed,
                "failed", failed
        ));
    }

    private void broadcastToWorkspace(String workspaceId, String eventName, Object data) {
        try {
            sseService.broadcastToWorkspace(workspaceId, eventName, data);
        } catch (Exception e) {
            log.warn("SSE 推送失败: event={}, workspaceId={}", eventName, workspaceId, e);
        }
    }
}
