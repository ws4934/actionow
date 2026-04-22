package com.actionow.project.dto;

import com.actionow.project.entity.Script;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 剧本详情响应 - 包含完整字段
 *
 * @author Actionow
 */
@Data
public class ScriptDetailResponse {

    private String id;
    private String workspaceId;
    private String title;
    private String status;
    private String synopsis;
    private String content;
    private String coverAssetId;
    private String coverUrl;
    private String docAssetId;
    private Integer episodeCount;
    private String currentVersionId;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;
    private Map<String, Object> extraInfo;

    public static ScriptDetailResponse fromEntity(Script script) {
        ScriptDetailResponse response = new ScriptDetailResponse();
        response.setId(script.getId());
        response.setWorkspaceId(script.getWorkspaceId());
        response.setTitle(script.getTitle());
        response.setStatus(script.getStatus());
        response.setSynopsis(script.getSynopsis());
        response.setContent(script.getContent());
        response.setCoverAssetId(script.getCoverAssetId());
        response.setDocAssetId(script.getDocAssetId());
        response.setCurrentVersionId(script.getCurrentVersionId());
        response.setVersionNumber(script.getVersionNumber());
        response.setCreatedAt(script.getCreatedAt());
        response.setUpdatedAt(script.getUpdatedAt());
        response.setCreatedBy(script.getCreatedBy());
        response.setExtraInfo(script.getExtraInfo());
        return response;
    }
}
