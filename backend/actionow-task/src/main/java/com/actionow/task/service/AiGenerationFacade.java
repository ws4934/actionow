package com.actionow.task.service;

import com.actionow.task.dto.*;
import com.actionow.task.entity.Task;

import java.util.List;
import java.util.Map;

/**
 * AI 生成任务门面接口
 * 提供 AI 生成任务的完整编排，包括：
 * - 分布式锁防止并发
 * - 积分冻结/解冻/确认消费
 * - 任务状态管理
 * - 素材状态同步
 *
 * @author Actionow
 */
public interface AiGenerationFacade {

    /**
     * 提交 AI 生成任务
     * 完整编排：分布式锁 → 验证 → 确定提供商 → 冻结积分 → 创建任务 → 更新素材状态 → 发送到队列
     *
     * @param request     提交请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 任务响应
     */
    GenerationTaskResponse submitGeneration(SubmitGenerationRequest request,
                                            String workspaceId, String userId);

    /**
     * 批量提交 AI 生成任务
     *
     * @param request     批量请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 批量响应
     */
    BatchGenerationResponse submitBatchGeneration(BatchGenerationRequest request,
                                                   String workspaceId, String userId);

    // ==================== 实体生成 API ====================

    /**
     * 提交实体生成任务
     * 一体化编排：分布式锁 → 创建Asset → 创建关联 → 存储参数 → 提交任务
     *
     * @param request     实体生成请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 实体生成响应
     */
    EntityGenerationResponse submitEntityGeneration(EntityGenerationRequest request,
                                                     String workspaceId, String userId);

    /**
     * 批量提交实体生成任务
     *
     * @param request     批量请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 批量响应
     */
    BatchEntityGenerationResponse submitBatchEntityGeneration(BatchEntityGenerationRequest request,
                                                               String workspaceId, String userId);

    /**
     * 重试生成任务
     * 从 Asset.extraInfo 读取原参数并合并覆盖
     *
     * @param request     重试请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 实体生成响应
     */
    EntityGenerationResponse retryGeneration(RetryGenerationRequest request,
                                              String workspaceId, String userId);

    /**
     * 查询生成状态
     *
     * @param assetId     素材 ID
     * @param workspaceId 工作空间 ID
     * @return 状态信息
     */
    Map<String, Object> getGenerationStatus(String assetId, String workspaceId);

    // ==================== 任务执行 API ====================

    /**
     * 执行 AI 任务（MQ 消费者调用）
     *
     * @param task 任务实体
     */
    void executeTask(Task task);

    /**
     * 处理任务完成回调
     *
     * @param taskId 任务 ID
     * @param result 执行结果
     */
    void handleCompletion(String taskId, ProviderExecutionResult result);

    /**
     * 取消 AI 生成任务
     * 取消任务并解冻积分
     *
     * @param taskId      任务 ID
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     */
    void cancelGeneration(String taskId, String workspaceId, String userId);

    /**
     * 获取可用的 AI 模型提供商列表
     *
     * @param providerType 提供商类型（IMAGE/VIDEO/AUDIO/TEXT）
     * @return 提供商列表
     */
    List<AvailableProviderResponse> getAvailableProviders(String providerType);

    /**
     * 预估积分消耗
     *
     * @param providerId 提供商 ID
     * @param params     用户输入参数
     * @return 积分预估结果（含 finalCost, baseCost, breakdown 等）
     */
    Map<String, Object> estimateCost(String providerId, Map<String, Object> params);

    /**
     * 调整任务优先级
     *
     * @param taskId      任务 ID
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param priority    新优先级 (1-5, 1最高)
     */
    void adjustTaskPriority(String taskId, String workspaceId, String userId, int priority);

    /**
     * 获取任务在队列中的位置
     *
     * @param taskId 任务 ID
     * @return 队列位置，-1表示不在队列中
     */
    int getQueuePosition(String taskId);
}
