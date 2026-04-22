package com.actionow.project.dto;

import com.actionow.project.entity.Storyboard;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分镜列表响应 - 只包含列表渲染所需字段
 *
 * @author Actionow
 */
@Data
public class StoryboardListResponse {

    private String id;
    private String scriptId;
    private String episodeId;
    private String title;
    private Integer sequence;
    private String status;
    private Integer duration;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;
    private String coverAssetId;
    private String coverUrl;

    public static StoryboardListResponse fromEntity(Storyboard storyboard) {
        StoryboardListResponse response = new StoryboardListResponse();
        response.setId(storyboard.getId());
        response.setScriptId(storyboard.getScriptId());
        response.setEpisodeId(storyboard.getEpisodeId());
        response.setTitle(storyboard.getTitle());
        response.setSequence(storyboard.getSequence());
        response.setStatus(storyboard.getStatus());
        response.setDuration(storyboard.getDuration());
        response.setVersionNumber(storyboard.getVersionNumber());
        response.setCreatedAt(storyboard.getCreatedAt());
        response.setUpdatedAt(storyboard.getUpdatedAt());
        response.setCreatedBy(storyboard.getCreatedBy());
        response.setCoverAssetId(storyboard.getCoverAssetId());
        return response;
    }
}
