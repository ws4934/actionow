package com.actionow.agent.controller;

import com.actionow.agent.context.memory.WorkingMemoryStore;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.dto.request.BatchEntityGenerationRequest;
import com.actionow.agent.dto.request.EntityGenerationRequest;
import com.actionow.agent.dto.request.RetryGenerationRequest;
import com.actionow.agent.dto.response.EntityGenerationResponse;
import com.actionow.agent.dto.response.SessionResponse;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.saa.factory.SaaAgentFactory;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.agent.scheduler.SessionArchiveScheduler;
import com.actionow.agent.service.EntityGenerationFacade;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.annotation.IgnoreAuth;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 内部控制器（SAA v2）
 * 提供服务间调用的内部 API
 * 基于 Spring AI Alibaba Agent Framework 实现
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
@IgnoreAuth
public class AgentInternalController {

    private final SaaSessionService sessionService;
    private final SessionArchiveScheduler sessionArchiveScheduler;
    private final EntityGenerationFacade entityGenerationFacade;
    private final SaaAgentFactory agentFactory;
    private final ExecutionRegistry executionRegistry;
    private final WorkingMemoryStore workingMemoryStore;

    /**
     * 调试端点开关。默认关闭，仅在运维排障时通过配置开启。
     * 即便 /internal/** 已被 HMAC token 保护，调试端点仍会泄露 JVM、活跃执行、WorkingMemory 键等细节，
     * 因此需要防御纵深再加一道闸门。
     */
    @Value("${actionow.agent.debug-endpoints-enabled:false}")
    private boolean debugEndpointsEnabled;

    /**
     * 获取工作空间的活跃会话列表（内部调用）
     */
    @GetMapping("/sessions/workspace/{workspaceId}")
    public Result<List<SessionResponse>> listWorkspaceSessions(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "50") int limit) {
        List<AgentSessionEntity> entities = sessionService.listWorkspaceSessions(workspaceId, limit);
        List<SessionResponse> responses = entities.stream()
                .map(SessionResponse::from)
                .toList();
        return Result.success(responses);
    }

    /**
     * 获取用户的会话列表（内部调用）
     */
    @GetMapping("/sessions/user/{userId}")
    public Result<List<SessionResponse>> listUserSessions(
            @PathVariable String userId,
            @RequestParam(defaultValue = "50") int limit) {
        List<AgentSessionEntity> entities = sessionService.listUserSessions(userId, limit);
        List<SessionResponse> responses = entities.stream()
                .map(SessionResponse::from)
                .toList();
        return Result.success(responses);
    }

    /**
     * 获取会话详情（内部调用）
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<SessionResponse> getSession(@PathVariable String sessionId) {
        AgentSessionEntity entity = sessionService.getSessionEntity(sessionId);
        return Result.success(SessionResponse.from(entity));
    }

    /**
     * 强制结束会话（内部调用）
     */
    @PostMapping("/sessions/{sessionId}/terminate")
    public Result<Void> terminateSession(@PathVariable String sessionId) {
        sessionService.endSession(sessionId);
        log.info("Session terminated internally: {}", sessionId);
        return Result.success();
    }

    /**
     * 执行会话维护任务（内部调用）
     * 包括：归档超过 30 天未活跃的会话、物理删除超过 90 天的软删除会话
     */
    @PostMapping("/maintenance")
    public Result<Map<String, Object>> runMaintenance() {
        log.info("Running session maintenance task...");
        SessionArchiveScheduler.MaintenanceResult result = sessionArchiveScheduler.runMaintenance();
        log.info("Session maintenance completed: {}", result);

        return Result.success(Map.of(
                "archivedCount", result.archivedCount(),
                "cleanedCount", result.cleanedCount(),
                "hasChanges", result.hasChanges()
        ));
    }

    /**
     * 归档空闲会话（内部调用）
     */
    @PostMapping("/maintenance/archive")
    public Result<Integer> archiveIdleSessions() {
        int archivedCount = sessionArchiveScheduler.archiveIdleSessions();
        return Result.success(archivedCount);
    }

    /**
     * 清理已删除会话（内部调用）
     */
    @PostMapping("/maintenance/cleanup")
    public Result<Integer> cleanupDeletedSessions() {
        int cleanedCount = sessionArchiveScheduler.cleanupDeletedSessions();
        return Result.success(cleanedCount);
    }

    // ==================== 实体生成接口 ====================

    /**
     * 提交实体生成任务
     */
    @PostMapping("/entity-generation")
    public Result<EntityGenerationResponse> submitEntityGeneration(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody EntityGenerationRequest request) {

        log.info("提交实体生成任务: workspaceId={}, userId={}, entityType={}, entityId={}",
                workspaceId, userId, request.getEntityType(), request.getEntityId());

        EntityGenerationResponse response = entityGenerationFacade.submitEntityGeneration(
                request, workspaceId, userId);

        if (Boolean.TRUE.equals(response.getSuccess())) {
            return Result.success(response);
        } else {
            return Result.fail(response.getErrorMessage());
        }
    }

    /**
     * 批量提交实体生成任务
     */
    @PostMapping("/entity-generation/batch")
    public Result<List<EntityGenerationResponse>> submitBatchEntityGeneration(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody BatchEntityGenerationRequest request) {

        log.info("批量提交实体生成任务: workspaceId={}, userId={}, count={}, parallel={}",
                workspaceId, userId,
                request.getRequests() != null ? request.getRequests().size() : 0,
                request.getParallel());

        List<EntityGenerationResponse> responses = entityGenerationFacade.submitBatchEntityGeneration(
                request, workspaceId, userId);

        return Result.success(responses);
    }

    /**
     * 重试生成任务
     */
    @PostMapping("/entity-generation/retry")
    public Result<EntityGenerationResponse> retryGeneration(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody RetryGenerationRequest request) {

        log.info("重试生成任务: workspaceId={}, userId={}, assetId={}",
                workspaceId, userId, request.getAssetId());

        EntityGenerationResponse response = entityGenerationFacade.retryGeneration(
                request, workspaceId, userId);

        if (Boolean.TRUE.equals(response.getSuccess())) {
            return Result.success(response);
        } else {
            return Result.fail(response.getErrorMessage());
        }
    }

    /**
     * 查询生成状态
     */
    @GetMapping("/entity-generation/{assetId}/status")
    public Result<Map<String, Object>> getGenerationStatus(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @PathVariable("assetId") String assetId) {

        log.debug("查询生成状态: workspaceId={}, assetId={}", workspaceId, assetId);

        Map<String, Object> status = entityGenerationFacade.getGenerationStatus(assetId, workspaceId);
        return Result.success(status);
    }

    // ==================== 诊断接口 ====================

    /**
     * 系统诊断端点 — 汇总 Agent 运行时关键状态
     * <p>
     * 包含：JVM 内存、Agent 工厂状态、活跃执行数、Working Memory 统计
     */
    @GetMapping("/debug/status")
    public Result<Map<String, Object>> debugStatus() {
        requireDebugEnabled();
        Map<String, Object> debug = new LinkedHashMap<>();

        // 1. 时间
        debug.put("timestamp", Instant.now().toString());

        // 2. JVM 内存
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMB", mem.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        jvm.put("heapMaxMB", mem.getHeapMemoryUsage().getMax() / (1024 * 1024));
        jvm.put("nonHeapUsedMB", mem.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
        jvm.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        debug.put("jvm", jvm);

        // 3. Agent 工厂
        debug.put("agentFactory", agentFactory.getSystemStatus());

        // 4. 活跃执行
        Map<String, Object> exec = new LinkedHashMap<>();
        exec.put("activeExecutions", executionRegistry.getActiveExecutionCount());
        exec.put("availablePermits", executionRegistry.getAvailablePermits());
        debug.put("execution", exec);

        // 5. Working Memory 全局概览
        Map<String, Object> wmStatus = new LinkedHashMap<>();
        wmStatus.put("info", "per-session stats available via /debug/session/{sessionId}");
        debug.put("workingMemory", wmStatus);

        return Result.success(debug);
    }

    /**
     * 单 Session 诊断端点
     */
    @GetMapping("/debug/session/{sessionId}")
    public Result<Map<String, Object>> debugSession(@PathVariable String sessionId) {
        requireDebugEnabled();
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("sessionId", sessionId);

        // Session 基本信息
        try {
            AgentSessionEntity entity = sessionService.getSessionEntity(sessionId);
            debug.put("status", entity.getStatus());
            debug.put("agentType", entity.getAgentType());
            debug.put("createdAt", entity.getCreatedAt());
            debug.put("lastActiveAt", entity.getLastActiveAt());
        } catch (Exception e) {
            debug.put("sessionError", e.getMessage());
        }

        // Working Memory
        int wmSize = workingMemoryStore.size(sessionId);
        debug.put("workingMemoryEntries", wmSize);
        if (wmSize > 0) {
            debug.put("workingMemoryKeys", workingMemoryStore.list(sessionId).stream()
                    .map(entry -> Map.of(
                            "key", entry.getKey(),
                            "source", entry.getSource() != null ? entry.getSource() : "manual",
                            "chars", entry.getCharCount(),
                            "pinned", entry.isPinned()))
                    .toList());
        }

        // Execution status
        debug.put("isCancelled", executionRegistry.isCancelled(sessionId));

        return Result.success(debug);
    }

    private void requireDebugEnabled() {
        if (!debugEndpointsEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(),
                    "调试端点未启用，请在配置中开启 actionow.agent.debug-endpoints-enabled");
        }
    }
}
