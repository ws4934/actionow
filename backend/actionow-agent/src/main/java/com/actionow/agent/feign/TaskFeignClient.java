package com.actionow.agent.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/**
 * 任务服务 Feign 客户端
 * Agent 模块通过 Task 模块提交 AI 生成任务（积分冻结、任务编排、MQ 发送）
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-task", path = "/internal/task",
        fallbackFactory = TaskFeignClientFallbackFactory.class)
public interface TaskFeignClient {

    /**
     * 提交 AI 生成任务
     * 走完整流程：积分冻结 → 任务创建 → MQ 发送 → AI 执行
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param request     生成请求参数
     * @return 包含 taskId, status, providerId, creditCost
     */
    @PostMapping("/ai/generate")
    Result<Map<String, Object>> submitAiGeneration(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> request);

    /**
     * 获取任务结果（轮询）
     *
     * @param taskId 任务 ID
     * @return 任务详情（含 status, outputResult）
     */
    @GetMapping("/{taskId}/result")
    Result<Map<String, Object>> getTaskResult(@PathVariable("taskId") String taskId);

    /**
     * 获取任务详情
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    Result<Map<String, Object>> getTask(@PathVariable("taskId") String taskId);

    /**
     * 取消任务（内部调用）
     *
     * @param taskId 任务 ID
     * @param userId 用户 ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/cancel-internal")
    Result<Void> cancelTask(@PathVariable("taskId") String taskId,
                            @RequestHeader("X-User-Id") String userId);

    // ==================== 批量作业 API ====================

    /**
     * 创建批量作业
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param request     批量作业请求
     * @return 包含 batchJobId, status, totalItems 等
     */
    @PostMapping("/batch-jobs")
    Result<Map<String, Object>> createBatchJob(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> request);

    /**
     * 获取批量作业状态
     *
     * @param batchJobId 批量作业 ID
     * @return 批量作业详情
     */
    @GetMapping("/batch-jobs/{batchJobId}")
    Result<Map<String, Object>> getBatchJob(@PathVariable("batchJobId") String batchJobId);

    /**
     * 取消批量作业
     *
     * @param batchJobId 批量作业 ID
     * @param userId     用户 ID
     * @return 操作结果
     */
    @PostMapping("/batch-jobs/{batchJobId}/cancel")
    Result<Void> cancelBatchJob(@PathVariable("batchJobId") String batchJobId,
                                 @RequestHeader("X-User-Id") String userId);

    /**
     * 获取批量作业子项列表
     *
     * @param batchJobId 批量作业 ID
     * @return 子项列表
     */
    @GetMapping("/batch-jobs/{batchJobId}/items")
    Result<List<Map<String, Object>>> getBatchJobItems(@PathVariable("batchJobId") String batchJobId);
}
