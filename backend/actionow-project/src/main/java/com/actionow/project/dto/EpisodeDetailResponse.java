package com.actionow.project.dto;

import com.actionow.project.entity.Episode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 剧集详情响应 - 包含完整字段
 *
 * @author Actionow
 */
@Data
public class EpisodeDetailResponse {

    private String id;
    private String workspaceId;
    private String scriptId;
    private String title;
    private Integer sequence;
    private String status;
    private String synopsis;
    private String content;
    private String coverAssetId;
    private String coverUrl;
    private String docAssetId;
    private Integer storyboardCount;
    private String currentVersionId;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;
    private Map<String, Object> extraInfo;

    public static EpisodeDetailResponse fromEntity(Episode episode) {
        EpisodeDetailResponse response = new EpisodeDetailResponse();
        response.setId(episode.getId());
        response.setWorkspaceId(episode.getWorkspaceId());
        response.setScriptId(episode.getScriptId());
        response.setTitle(episode.getTitle());
        response.setSequence(episode.getSequence());
        response.setStatus(episode.getStatus());
        response.setSynopsis(episode.getSynopsis());
        response.setContent(episode.getContent());
        response.setCoverAssetId(episode.getCoverAssetId());
        response.setDocAssetId(episode.getDocAssetId());
        response.setCurrentVersionId(episode.getCurrentVersionId());
        response.setVersionNumber(episode.getVersionNumber());
        response.setCreatedAt(episode.getCreatedAt());
        response.setUpdatedAt(episode.getUpdatedAt());
        response.setCreatedBy(episode.getCreatedBy());
        response.setExtraInfo(episode.getExtraInfo());
        return response;
    }
}
