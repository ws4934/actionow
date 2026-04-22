package com.actionow.task.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 实体生成请求
 * 一体化接口：传入实体信息 → 自动创建Asset → 自动创建关联 → 提交任务
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
public class EntityGenerationRequest extends BaseGenerationRequest {

    /**
     * 实体类型
     * 支持: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE, ASSET
     */
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    /**
     * 实体 ID
     */
    @NotBlank(message = "实体ID不能为空")
    private String entityId;

    /**
     * 素材名称（可选，自动生成）
     */
    private String assetName;

    /**
     * 关联类型: DRAFT(默认)/OFFICIAL/REFERENCE
     */
    private String relationType;

    /**
     * 剧本 ID（可选，设置素材作用域）
     */
    private String scriptId;

    /**
     * 参考素材 ID 列表（img2img 等场景）
     */
    private List<String> referenceAssetIds;
}
