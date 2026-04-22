package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 素材版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 素材ID
     */
    private String assetId;

    /**
     * 版本号
     */
    private Integer versionNumber;

    /**
     * 变更摘要
     */
    private String changeSummary;

    /**
     * 创建人ID
     */
    private String createdBy;

    /**
     * 创建人名称
     */
    private String createdByName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 是否为当前版本
     */
    private Boolean isCurrent;

    // ========== 素材业务字段快照 ==========

    /**
     * 作用域: WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID
     */
    private String scriptId;

    /**
     * 素材名称
     */
    private String name;

    /**
     * 素材描述
     */
    private String description;

    /**
     * 素材类型: IMAGE, VIDEO, AUDIO, DOCUMENT
     */
    private String assetType;

    /**
     * 来源: UPLOAD, AI_GENERATED, EXTERNAL
     */
    private String source;

    /**
     * 文件路径
     */
    private String fileKey;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 元数据
     */
    private Map<String, Object> metaInfo;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 生成状态
     */
    private String generationStatus;

    /**
     * 工作流ID
     */
    private String workflowId;

    /**
     * 任务ID
     */
    private String taskId;
}
