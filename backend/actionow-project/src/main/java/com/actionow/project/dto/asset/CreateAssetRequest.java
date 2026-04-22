package com.actionow.project.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 创建素材请求 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssetRequest {

    /**
     * 素材名称
     */
    @NotBlank(message = "素材名称不能为空")
    @Size(max = 500, message = "素材名称不能超过500个字符")
    private String name;

    /**
     * 描述
     */
    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    /**
     * 素材类型: IMAGE, VIDEO, AUDIO, DOCUMENT, MODEL, OTHER
     */
    @NotBlank(message = "素材类型不能为空")
    private String assetType;

    /**
     * 来源: UPLOAD, SYSTEM, AI_GENERATED
     */
    private String source;

    /**
     * 作用域: WORKSPACE(默认), SCRIPT
     */
    private String scope;

    /**
     * 剧本ID（scope为SCRIPT时必填）
     */
    private String scriptId;

    /**
     * 文件名（用于上传）
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 生成状态（AI生成时使用）
     * 值: DRAFT, GENERATING, COMPLETED, FAILED
     */
    private String generationStatus;

    /**
     * 关联实体列表
     * 用于在 Canvas 中创建素材与其他实体（角色、场景、道具等）的关联边
     */
    private List<RelatedEntityInfo> relatedEntities;

    /**
     * 关联实体信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedEntityInfo {
        /**
         * 实体类型 (CHARACTER, SCENE, PROP, STYLE, STORYBOARD)
         */
        private String entityType;
        /**
         * 实体ID
         */
        private String entityId;
        /**
         * 关系类型（可选）
         */
        private String relationType;
    }
}
