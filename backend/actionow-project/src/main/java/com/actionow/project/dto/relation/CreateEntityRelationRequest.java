package com.actionow.project.dto.relation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 创建实体关系请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEntityRelationRequest {

    /**
     * 源实体类型: STORYBOARD, EPISODE, CHARACTER 等
     */
    @NotBlank(message = "源实体类型不能为空")
    private String sourceType;

    /**
     * 源实体ID
     */
    @NotBlank(message = "源实体ID不能为空")
    private String sourceId;

    /**
     * 源实体版本ID（可选）
     */
    private String sourceVersionId;

    /**
     * 目标实体类型: CHARACTER, SCENE, PROP, STYLE 等
     */
    @NotBlank(message = "目标实体类型不能为空")
    private String targetType;

    /**
     * 目标实体ID
     */
    @NotBlank(message = "目标实体ID不能为空")
    private String targetId;

    /**
     * 目标实体版本ID（可选）
     */
    private String targetVersionId;

    /**
     * 关系类型: appears_in, takes_place_in, uses, speaks_in 等
     */
    @NotBlank(message = "关系类型不能为空")
    private String relationType;

    /**
     * 关系标签（可选的显示名称）
     */
    @Size(max = 100, message = "关系标签不能超过100个字符")
    private String relationLabel;

    /**
     * 描述
     */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 扩展信息（存储关系的元数据）
     */
    private Map<String, Object> extraInfo;
}
