package com.actionow.project.dto.library;

import com.actionow.project.entity.Asset;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共库素材响应
 *
 * @author Actionow
 */
@Data
public class LibraryAssetResponse {

    private String id;
    private String name;
    private String description;
    private String assetType;
    private String fileUrl;
    private String thumbnailUrl;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime publishedAt;
    private String publishNote;
    private LocalDateTime createdAt;

    public static LibraryAssetResponse fromEntity(Asset a) {
        LibraryAssetResponse r = new LibraryAssetResponse();
        r.setId(a.getId());
        r.setName(a.getName());
        r.setDescription(a.getDescription());
        r.setAssetType(a.getAssetType());
        r.setFileUrl(a.getFileUrl());
        r.setThumbnailUrl(a.getThumbnailUrl());
        r.setFileSize(a.getFileSize());
        r.setMimeType(a.getMimeType());
        r.setPublishedAt(a.getPublishedAt());
        r.setPublishNote(a.getPublishNote());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }
}
