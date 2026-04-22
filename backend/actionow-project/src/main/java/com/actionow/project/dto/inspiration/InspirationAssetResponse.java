package com.actionow.project.dto.inspiration;

import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.entity.AssetMetaInfo;
import com.actionow.project.entity.InspirationRecordAsset;
import lombok.Data;

/**
 * 灵感资产响应
 *
 * @author Actionow
 */
@Data
public class InspirationAssetResponse {

    private String id;
    private String url;
    private String thumbnailUrl;
    private String assetType;
    private Integer width;
    private Integer height;
    private Double duration;
    private String mimeType;
    private Long fileSize;

    public static InspirationAssetResponse fromEntity(InspirationRecordAsset asset) {
        InspirationAssetResponse response = new InspirationAssetResponse();
        // 优先使用 asset_id（指向 t_asset），fallback 到自身 PK（旧数据兼容）
        response.setId(asset.getAssetId() != null ? asset.getAssetId() : asset.getId());
        response.setUrl(asset.getUrl());
        response.setThumbnailUrl(asset.getThumbnailUrl());
        response.setAssetType(asset.getAssetType());
        response.setWidth(asset.getWidth());
        response.setHeight(asset.getHeight());
        response.setDuration(asset.getDuration());
        response.setMimeType(asset.getMimeType());
        response.setFileSize(asset.getFileSize());
        return response;
    }

    public static InspirationAssetResponse fromAssetResponse(AssetResponse asset) {
        InspirationAssetResponse response = new InspirationAssetResponse();
        response.setId(asset.getId());
        response.setUrl(asset.getFileUrl());
        response.setThumbnailUrl(asset.getThumbnailUrl());
        response.setAssetType(asset.getAssetType());
        response.setMimeType(asset.getMimeType());
        response.setFileSize(asset.getFileSize());
        AssetMetaInfo meta = AssetMetaInfo.fromMap(asset.getMetaInfo());
        response.setWidth(meta.getWidth());
        response.setHeight(meta.getHeight());
        response.setDuration(meta.getDuration());
        return response;
    }
}
