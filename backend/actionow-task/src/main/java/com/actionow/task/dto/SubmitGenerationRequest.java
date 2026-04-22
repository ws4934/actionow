package com.actionow.task.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 提交 AI 生成请求
 * <p>
 * 所有生成参数统一通过 params 传递，不对具体参数做特殊处理。
 * 不同模型可能有不同的参数需求，保持最大灵活性。
 *
 * @author Actionow
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SubmitGenerationRequest extends BaseGenerationRequest {

    /**
     * 关联的素材 ID（IMAGE/VIDEO 必填，TEXT 可选）
     */
    private String assetId;

    /**
     * 任务标题（可选）
     */
    private String title;

    // ==================== 内部透传字段（不对外暴露） ====================

    /**
     * 源实体类型（CHARACTER/SCENE 等），由 submitEntityGeneration 透传，
     * 存入 input_params，在任务完成时一并更新到 entity_type/entity_id，
     * 避免 MQ 发出后再做独立 UPDATE 引发乐观锁竞争。
     */
    private String entityType;

    /**
     * 源实体 ID，同上
     */
    private String entityId;

    /**
     * 源实体名称（用于任务列表显示），同上
     */
    private String entityName;

    /**
     * 所属剧本 ID，直接写入 task.script_id
     */
    private String scriptId;
}
