package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 实体生成响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityGenerationResponse {

    /**
     * 创建的素材 ID
     */
    private String assetId;

    /**
     * 关联 ID（entityType=ASSET 时为 null）
     */
    private String relationId;

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务状态: PENDING/RUNNING
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
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 创建成功响应
     */
    public static EntityGenerationResponse success(String assetId, String relationId,
                                                    String taskId, String taskStatus,
                                                    String providerId, Long creditCost,
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
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
