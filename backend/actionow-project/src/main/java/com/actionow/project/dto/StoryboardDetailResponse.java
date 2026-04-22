package com.actionow.project.dto;

import com.actionow.project.dto.relation.StoryboardCharacterRelation;
import com.actionow.project.dto.relation.StoryboardDialogueRelation;
import com.actionow.project.dto.relation.StoryboardPropRelation;
import com.actionow.project.dto.relation.StoryboardSceneRelation;
import com.actionow.project.entity.Storyboard;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分镜详情响应 - 包含完整字段和关联实体
 *
 * @author Actionow
 */
@Data
public class StoryboardDetailResponse {

    private String id;
    private String workspaceId;
    private String scriptId;
    private String episodeId;
    private String title;
    private Integer sequence;
    private String status;
    private String synopsis;
    private Integer duration;

    /**
     * 视觉描述（仅镜头属性）
     */
    private Map<String, Object> visualDesc;

    /**
     * 音频描述（仅非实体属性）
     */
    private Map<String, Object> audioDesc;

    private String currentVersionId;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;
    private Map<String, Object> extraInfo;
    private String coverAssetId;
    private String coverUrl;

    // ==================== 关联实体（从 entity_relation 表获取） ====================

    /**
     * 场景关系
     */
    private StoryboardSceneRelation scene;

    /**
     * 角色出现关系列表
     */
    private List<StoryboardCharacterRelation> characters;

    /**
     * 道具使用关系列表
     */
    private List<StoryboardPropRelation> props;

    /**
     * 对白关系列表
     */
    private List<StoryboardDialogueRelation> dialogues;

    /**
     * 风格ID
     */
    private String styleId;

    /**
     * 风格名称
     */
    private String styleName;

    /**
     * 从实体转换（不包含关系数据，需要单独填充）
     */
    public static StoryboardDetailResponse fromEntity(Storyboard storyboard) {
        StoryboardDetailResponse response = new StoryboardDetailResponse();
        response.setId(storyboard.getId());
        response.setWorkspaceId(storyboard.getWorkspaceId());
        response.setScriptId(storyboard.getScriptId());
        response.setEpisodeId(storyboard.getEpisodeId());
        response.setTitle(storyboard.getTitle());
        response.setSequence(storyboard.getSequence());
        response.setStatus(storyboard.getStatus());
        response.setSynopsis(storyboard.getSynopsis());
        response.setDuration(storyboard.getDuration());
        response.setVisualDesc(storyboard.getVisualDesc());
        response.setAudioDesc(storyboard.getAudioDesc());
        response.setCurrentVersionId(storyboard.getCurrentVersionId());
        response.setVersionNumber(storyboard.getVersionNumber());
        response.setCreatedAt(storyboard.getCreatedAt());
        response.setUpdatedAt(storyboard.getUpdatedAt());
        response.setCreatedBy(storyboard.getCreatedBy());
        response.setExtraInfo(storyboard.getExtraInfo());
        response.setCoverAssetId(storyboard.getCoverAssetId());
        return response;
    }
}
