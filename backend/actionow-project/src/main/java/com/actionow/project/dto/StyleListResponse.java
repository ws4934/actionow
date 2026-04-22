package com.actionow.project.dto;

import com.actionow.project.entity.Style;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风格列表响应 - 只包含列表渲染所需字段，不含 styleParams
 *
 * @author Actionow
 */
@Data
public class StyleListResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String scriptId;
    private String name;
    private String description;
    private String coverAssetId;
    private String coverUrl;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;

    public static StyleListResponse fromEntity(Style style) {
        StyleListResponse response = new StyleListResponse();
        response.setId(style.getId());
        response.setWorkspaceId(style.getWorkspaceId());
        response.setScope(style.getScope());
        response.setScriptId(style.getScriptId());
        response.setName(style.getName());
        response.setDescription(style.getDescription());
        response.setCoverAssetId(style.getCoverAssetId());
        response.setVersionNumber(style.getVersionNumber());
        response.setCreatedAt(style.getCreatedAt());
        response.setUpdatedAt(style.getUpdatedAt());
        response.setCreatedBy(style.getCreatedBy());
        return response;
    }
}
