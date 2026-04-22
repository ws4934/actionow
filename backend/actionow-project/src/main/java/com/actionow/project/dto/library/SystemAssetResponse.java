package com.actionow.project.dto.library;

import com.actionow.project.entity.Asset;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统管理员视图素材响应
 *
 * @author Actionow
 */
@Data
public class SystemAssetResponse {

    private String id;
    private String name;
    private String description;
    private String assetType;
    private String scope;
    private String fileUrl;
    private String thumbnailUrl;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private String publishNote;
    private LocalDateTime createdAt;
    private String createdBy;

    public static SystemAssetResponse fromEntity(Asset a) {
        SystemAssetResponse r = new SystemAssetResponse();
        r.setId(a.getId());
        r.setName(a.getName());
        r.setDescription(a.getDescription());
        r.setAssetType(a.getAssetType());
        r.setScope(a.getScope());
        r.setFileUrl(a.getFileUrl());
        r.setThumbnailUrl(a.getThumbnailUrl());
        r.setFileSize(a.getFileSize());
        r.setMimeType(a.getMimeType());
        r.setPublishedAt(a.getPublishedAt());
        r.setPublishedBy(a.getPublishedBy());
        r.setPublishNote(a.getPublishNote());
        r.setCreatedAt(a.getCreatedAt());
        r.setCreatedBy(a.getCreatedBy());
        return r;
    }
}
