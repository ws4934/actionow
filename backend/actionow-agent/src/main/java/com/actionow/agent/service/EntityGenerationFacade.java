package com.actionow.agent.service;

import com.actionow.agent.dto.request.BatchEntityGenerationRequest;
import com.actionow.agent.dto.request.EntityGenerationRequest;
import com.actionow.agent.dto.request.RetryGenerationRequest;
import com.actionow.agent.dto.response.EntityGenerationResponse;

import java.util.List;
import java.util.Map;

/**
 * 实体生成门面服务接口
 * 提供一体化的 AI 生成接口：用户传入实体ID和生成参数 → 自动创建Asset → 自动创建关联 → 提交任务
 *
 * @author Actionow
 */
public interface EntityGenerationFacade {

    /**
     * 提交实体生成任务
     *
     * 流程：
     * 1. 获取分布式锁 (entity_generation:TYPE:ID)
     * 2. 创建 Asset（状态 GENERATING）
     * 3. 创建 EntityAssetRelation（entityType=ASSET 时跳过）
     * 4. 更新 Asset.extraInfo 存储完整生成参数
     * 5. 提交 AI 生成任务
     *
     * @param request     生成请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 生成响应
     */
    EntityGenerationResponse submitEntityGeneration(
            EntityGenerationRequest request,
            String workspaceId,
            String userId);

    /**
     * 批量提交实体生成任务
     *
     * @param request     批量请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 生成响应列表
     */
    List<EntityGenerationResponse> submitBatchEntityGeneration(
            BatchEntityGenerationRequest request,
            String workspaceId,
            String userId);

    /**
     * 重试生成任务
     * 从 Asset.extraInfo 读取原参数并合并覆盖
     *
     * @param request     重试请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 生成响应
     */
    EntityGenerationResponse retryGeneration(
            RetryGenerationRequest request,
            String workspaceId,
            String userId);

    /**
     * 查询生成状态
     *
     * @param assetId     素材 ID
     * @param workspaceId 工作空间 ID
     * @return 状态信息
     */
    Map<String, Object> getGenerationStatus(String assetId, String workspaceId);
}
