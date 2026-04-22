package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成任务响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationTaskResponse {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 关联的素材 ID
     */
    private String assetId;

    /**
     * 模型提供商 ID
     */
    private String providerId;

    /**
     * 模型提供商名称
     */
    private String providerName;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 预估消耗积分
     */
    private Long creditCost;
}
