package com.actionow.project.dto;

import com.actionow.project.entity.Style;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 风格详情响应 - 包含完整字段（含 styleParams, extraInfo）
 *
 * @author Actionow
 */
@Data
public class StyleDetailResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String scriptId;
    private String name;
    private String description;
    private String fixedDesc;
    private Map<String, Object> styleParams;
    private String coverAssetId;
    private String coverUrl;
    private String currentVersionId;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;
    private Map<String, Object> extraInfo;

    public static StyleDetailResponse fromEntity(Style style) {
        StyleDetailResponse response = new StyleDetailResponse();
        response.setId(style.getId());
        response.setWorkspaceId(style.getWorkspaceId());
        response.setScope(style.getScope());
        response.setScriptId(style.getScriptId());
        response.setName(style.getName());
        response.setDescription(style.getDescription());
        response.setFixedDesc(style.getFixedDesc());
        response.setStyleParams(style.getStyleParams());
        response.setCoverAssetId(style.getCoverAssetId());
        response.setCurrentVersionId(style.getCurrentVersionId());
        response.setVersionNumber(style.getVersionNumber());
        response.setCreatedAt(style.getCreatedAt());
        response.setUpdatedAt(style.getUpdatedAt());
        response.setCreatedBy(style.getCreatedBy());
        response.setExtraInfo(style.getExtraInfo());
        return response;
    }
}
