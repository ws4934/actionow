package com.actionow.project.dto.relation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 挂载素材到实体请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MountAssetRequest {

    /**
     * 实体类型: CHARACTER, SCENE, PROP, STYLE, EPISODE, SCRIPT, STORYBOARD
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
     * 关联类型（可选，默认 DRAFT）
     */
    private String relationType;

    /**
     * 描述（可选）
     */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /**
     * 排序（可选）
     */
    private Integer sequence;
}
