package com.actionow.project.dto;

import com.actionow.project.entity.Scene;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 场景详情响应 - 包含完整字段（含 appearanceData, extraInfo）
 *
 * @author Actionow
 */
@Data
public class SceneDetailResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String sceneType;
    private String scriptId;
    private String name;
    private String description;
    private String fixedDesc;
    private Map<String, Object> appearanceData;
    private String coverAssetId;
    private String coverUrl;
    private String voiceAssetId;
    private String voiceUrl;
    private String currentVersionId;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;
    private Map<String, Object> extraInfo;

    public static SceneDetailResponse fromEntity(Scene scene) {
        SceneDetailResponse response = new SceneDetailResponse();
        response.setId(scene.getId());
        response.setWorkspaceId(scene.getWorkspaceId());
        response.setScope(scene.getScope());
        response.setSceneType(scene.getSceneType());
        response.setScriptId(scene.getScriptId());
        response.setName(scene.getName());
        response.setDescription(scene.getDescription());
        response.setFixedDesc(scene.getFixedDesc());
        response.setAppearanceData(scene.getAppearanceData());
        response.setCoverAssetId(scene.getCoverAssetId());
        response.setCurrentVersionId(scene.getCurrentVersionId());
        response.setVersionNumber(scene.getVersionNumber());
        response.setCreatedAt(scene.getCreatedAt());
        response.setUpdatedAt(scene.getUpdatedAt());
        response.setCreatedBy(scene.getCreatedBy());
        response.setExtraInfo(scene.getExtraInfo());
        return response;
    }
}
