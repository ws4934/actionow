package com.actionow.project.dto.relation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 创建实体-素材关联请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEntityAssetRelationRequest {

    /**
     * 实体类型: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE
     */
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    /**
     * 实体ID
     */
    @NotBlank(message = "实体ID不能为空")
    private String entityId;

    /**
     * 素材ID
     */
    @NotBlank(message = "素材ID不能为空")
    private String assetId;

    /**
     * 关联类型: REFERENCE, OFFICIAL, DRAFT（默认为 DRAFT）
     */
    private String relationType;

    /**
     * 描述
     */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /**
     * 排序
     */
    private Integer sequence;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
