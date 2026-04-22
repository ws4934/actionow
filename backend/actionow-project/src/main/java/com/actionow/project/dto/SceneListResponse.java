package com.actionow.project.dto;

import com.actionow.project.entity.Scene;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景列表响应 - 只包含列表渲染所需字段，不含 appearanceData
 *
 * @author Actionow
 */
@Data
public class SceneListResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String sceneType;
    private String scriptId;
    private String name;
    private String description;
    private String coverAssetId;
    private String coverUrl;
    private String voiceAssetId;
    private String voiceUrl;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;
    private String createdByNickname;

    public static SceneListResponse fromEntity(Scene scene) {
        SceneListResponse response = new SceneListResponse();
        response.setId(scene.getId());
        response.setWorkspaceId(scene.getWorkspaceId());
        response.setScope(scene.getScope());
        response.setSceneType(scene.getSceneType());
        response.setScriptId(scene.getScriptId());
        response.setName(scene.getName());
        response.setDescription(scene.getDescription());
        response.setCoverAssetId(scene.getCoverAssetId());
        response.setVersionNumber(scene.getVersionNumber());
        response.setCreatedAt(scene.getCreatedAt());
        response.setUpdatedAt(scene.getUpdatedAt());
        response.setCreatedBy(scene.getCreatedBy());
        return response;
    }
}
