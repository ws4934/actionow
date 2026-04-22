package com.actionow.project.dto.asset;

import com.actionow.project.entity.Asset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 素材响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String scriptId;
    private String name;
    private String description;
    private String assetType;
    private String source;
    private String fileKey;
    private String fileUrl;
    private String thumbnailUrl;
    private Long fileSize;
    private String mimeType;
    private Map<String, Object> metaInfo;
    private Map<String, Object> extraInfo;
    private String generationStatus;
    private String workflowId;
    private String taskId;
    private String createdBy;
    private String createdByNickname;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 当前版本记录ID
     */
    private String currentVersionId;

    /**
     * 业务版本号
     */
    private Integer versionNumber;

    /**
     * 删除时间（回收站素材专用）
     */
    private LocalDateTime deletedAt;

    /**
     * 回收站文件路径（回收站素材专用）
     */
    private String trashPath;

    public static AssetResponse fromEntity(Asset asset) {
        return AssetResponse.builder()
                .id(asset.getId())
                .workspaceId(asset.getWorkspaceId())
                .scope(asset.getScope())
                .scriptId(asset.getScriptId())
                .name(asset.getName())
                .description(asset.getDescription())
                .assetType(asset.getAssetType())
                .source(asset.getSource())
                .fileKey(asset.getFileKey())
                .fileUrl(asset.getFileUrl())
                .thumbnailUrl(asset.getThumbnailUrl())
                .fileSize(asset.getFileSize())
                .mimeType(asset.getMimeType())
                .metaInfo(asset.getMetaInfo())
                .extraInfo(asset.getExtraInfo())
                .generationStatus(asset.getGenerationStatus())
                .workflowId(asset.getWorkflowId())
                .taskId(asset.getTaskId())
                .createdBy(asset.getCreatedBy())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .currentVersionId(asset.getCurrentVersionId())
                .versionNumber(asset.getVersionNumber())
                .deletedAt(asset.getDeletedAt())
                .trashPath(asset.getTrashPath())
                .build();
    }
}
