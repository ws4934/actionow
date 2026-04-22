package com.actionow.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体生成请求 DTO
 * 用于提交 AI 生成任务，自动创建 Asset 和 EntityAssetRelation
 * <p>
 * 所有生成参数统一通过 params 传递，不对具体参数做特殊处理。
 * 不同模型可能有不同的参数需求，保持最大灵活性。
 *
 * @author Actionow
 */
@Data
public class EntityGenerationRequest {

    /**
     * 实体类型
     * 支持: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE, ASSET
     */
    @NotBlank(message = "entityType 不能为空")
    private String entityType;

    /**
     * 实体 ID
     */
    @NotBlank(message = "entityId 不能为空")
    private String entityId;

    /**
     * 生成类型: IMAGE, VIDEO, AUDIO
     */
    @NotBlank(message = "generationType 不能为空")
    private String generationType;

    /**
     * 模型提供商 ID（可选，不指定则自动选择）
     */
    private String providerId;

    /**
     * 生成参数（所有参数统一在此传递，如 prompt, negative_prompt, width, height 等）
     */
    private Map<String, Object> params = new HashMap<>();

    /**
     * 素材名称（可选，不指定则自动生成）
     */
    private String assetName;

    /**
     * 关联类型（可选）
     * DRAFT（默认）- 草稿
     * OFFICIAL - 正式
     * REFERENCE - 参考
     */
    private String relationType = "DRAFT";

    /**
     * 剧本 ID（可选）
     * 设置素材的作用域，不指定则为 WORKSPACE 级别
     */
    private String scriptId;

    /**
     * 优先级（可选）
     * 数值越大优先级越高
     */
    private Integer priority;

    /**
     * 参考素材 ID 列表（可选）
     * 用于 img2img、视频生成等需要参考素材的场景
     */
    private List<String> referenceAssetIds;
}
