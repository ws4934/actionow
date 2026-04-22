package com.actionow.agent.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 实体生成响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
public class EntityGenerationResponse {

    /**
     * 创建的素材 ID
     */
    private String assetId;

    /**
     * 关联 ID
     * 当 entityType 为 ASSET 时为 null（素材衍生场景不创建关联）
     */
    private String relationId;

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务状态
     * PENDING - 等待中
     * QUEUED - 已入队
     * RUNNING - 执行中
     */
    private String taskStatus;

    /**
     * 使用的模型提供商 ID
     */
    private String providerId;

    /**
     * 消耗积分
     */
    private Long creditCost;

    /**
     * 完整生成参数（供重试使用）
     */
    private Map<String, Object> generationParams;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 创建成功响应
     */
    public static EntityGenerationResponse success(String assetId, String relationId, String taskId,
                                                   String taskStatus, String providerId, Long creditCost,
                                                   Map<String, Object> generationParams) {
        return EntityGenerationResponse.builder()
                .assetId(assetId)
                .relationId(relationId)
                .taskId(taskId)
                .taskStatus(taskStatus)
                .providerId(providerId)
                .creditCost(creditCost)
                .generationParams(generationParams)
                .success(true)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static EntityGenerationResponse fail(String assetId, String errorMessage) {
        return EntityGenerationResponse.builder()
                .assetId(assetId)
                .errorMessage(errorMessage)
                .success(false)
                .build();
    }
}
