package com.actionow.project.dto;

import com.actionow.project.entity.Script;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剧本列表响应 - 只包含列表渲染所需字段
 *
 * @author Actionow
 */
@Data
public class ScriptListResponse {

    private String id;
    private String workspaceId;
    private String title;
    private String status;
    private String synopsis;
    private String coverAssetId;
    private String coverUrl;
    private Integer episodeCount;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;

    public static ScriptListResponse fromEntity(Script script) {
        ScriptListResponse response = new ScriptListResponse();
        response.setId(script.getId());
        response.setWorkspaceId(script.getWorkspaceId());
        response.setTitle(script.getTitle());
        response.setStatus(script.getStatus());
        response.setSynopsis(script.getSynopsis());
        response.setCoverAssetId(script.getCoverAssetId());
        response.setVersionNumber(script.getVersionNumber());
        response.setCreatedAt(script.getCreatedAt());
        response.setUpdatedAt(script.getUpdatedAt());
        response.setCreatedBy(script.getCreatedBy());
        return response;
    }
}
