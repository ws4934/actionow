package com.actionow.project.dto;

import com.actionow.project.entity.Episode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剧集列表响应 - 只包含列表渲染所需字段
 *
 * @author Actionow
 */
@Data
public class EpisodeListResponse {

    private String id;
    private String scriptId;
    private String title;
    private String synopsis;
    private Integer sequence;
    private String status;
    private String coverAssetId;
    private String coverUrl;
    private Integer storyboardCount;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;

    public static EpisodeListResponse fromEntity(Episode episode) {
        EpisodeListResponse response = new EpisodeListResponse();
        response.setId(episode.getId());
        response.setScriptId(episode.getScriptId());
        response.setTitle(episode.getTitle());
        response.setSynopsis(episode.getSynopsis());
        response.setSequence(episode.getSequence());
        response.setStatus(episode.getStatus());
        response.setCoverAssetId(episode.getCoverAssetId());
        response.setVersionNumber(episode.getVersionNumber());
        response.setCreatedAt(episode.getCreatedAt());
        response.setUpdatedAt(episode.getUpdatedAt());
        response.setCreatedBy(episode.getCreatedBy());
        return response;
    }
}
