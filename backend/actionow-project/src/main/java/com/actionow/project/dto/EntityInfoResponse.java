package com.actionow.project.dto;

import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.entity.Character;
import com.actionow.project.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 实体信息响应 DTO
 * 用于 Canvas 服务 Feign 调用返回的通用实体信息
 * 结构与 actionow-canvas 的 EntityInfo 保持一致
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityInfoResponse {

    /**
     * 实体ID
     */
    private String id;

    /**
     * 实体类型
     */
    private String entityType;

    /**
     * 实体名称
     */
    private String name;

    /**
     * 实体描述
     */
    private String description;

    /**
     * 封面/缩略图URL
     */
    private String coverUrl;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 状态
     */
    private String status;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 实体详情 - 包含实体类型特有的字段
     */
    private Map<String, Object> detail;

    /**
     * 从 Character 实体转换
     */
    public static EntityInfoResponse fromCharacter(Character character, String coverUrl) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scope", character.getScope());
        detail.put("scriptId", character.getScriptId());
        detail.put("fixedDesc", character.getFixedDesc());
        detail.put("age", character.getAge());
        detail.put("gender", character.getGender());
        detail.put("characterType", character.getCharacterType());
        detail.put("voiceSeedId", character.getVoiceSeedId());
        detail.put("appearanceData", character.getAppearanceData());
        detail.put("coverAssetId", character.getCoverAssetId());
        detail.put("currentVersionId", character.getCurrentVersionId());
        detail.put("versionNumber", character.getVersionNumber());
        detail.put("extraInfo", character.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(character.getId())
                .entityType("CHARACTER")
                .name(character.getName())
                .description(character.getDescription())
                .coverUrl(coverUrl)
                .version(character.getVersion())
                .updatedAt(character.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 Scene 实体转换
     */
    public static EntityInfoResponse fromScene(Scene scene, String coverUrl) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scope", scene.getScope());
        detail.put("sceneType", scene.getSceneType());
        detail.put("scriptId", scene.getScriptId());
        detail.put("fixedDesc", scene.getFixedDesc());
        detail.put("appearanceData", scene.getAppearanceData());
        detail.put("coverAssetId", scene.getCoverAssetId());
        detail.put("currentVersionId", scene.getCurrentVersionId());
        detail.put("versionNumber", scene.getVersionNumber());
        detail.put("extraInfo", scene.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(scene.getId())
                .entityType("SCENE")
                .name(scene.getName())
                .description(scene.getDescription())
                .coverUrl(coverUrl)
                .version(scene.getVersion())
                .updatedAt(scene.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 Prop 实体转换
     */
    public static EntityInfoResponse fromProp(Prop prop, String coverUrl) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scope", prop.getScope());
        detail.put("scriptId", prop.getScriptId());
        detail.put("fixedDesc", prop.getFixedDesc());
        detail.put("propType", prop.getPropType());
        detail.put("appearanceData", prop.getAppearanceData());
        detail.put("coverAssetId", prop.getCoverAssetId());
        detail.put("currentVersionId", prop.getCurrentVersionId());
        detail.put("versionNumber", prop.getVersionNumber());
        detail.put("extraInfo", prop.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(prop.getId())
                .entityType("PROP")
                .name(prop.getName())
                .description(prop.getDescription())
                .coverUrl(coverUrl)
                .version(1)
                .updatedAt(prop.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 Style 实体转换
     */
    public static EntityInfoResponse fromStyle(Style style, String coverUrl) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scope", style.getScope());
        detail.put("scriptId", style.getScriptId());
        detail.put("fixedDesc", style.getFixedDesc());
        detail.put("styleParams", style.getStyleParams());
        detail.put("coverAssetId", style.getCoverAssetId());
        detail.put("currentVersionId", style.getCurrentVersionId());
        detail.put("versionNumber", style.getVersionNumber());
        detail.put("extraInfo", style.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(style.getId())
                .entityType("STYLE")
                .name(style.getName())
                .description(style.getDescription())
                .coverUrl(coverUrl)
                .version(style.getVersion())
                .updatedAt(style.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 Episode 实体转换
     */
    public static EntityInfoResponse fromEpisode(Episode episode, String coverUrl) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scriptId", episode.getScriptId());
        detail.put("title", episode.getTitle());
        detail.put("sequence", episode.getSequence());
        detail.put("synopsis", episode.getSynopsis());
        detail.put("content", episode.getContent());
        detail.put("coverAssetId", episode.getCoverAssetId());
        detail.put("docAssetId", episode.getDocAssetId());
        detail.put("currentVersionId", episode.getCurrentVersionId());
        detail.put("versionNumber", episode.getVersionNumber());
        detail.put("extraInfo", episode.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(episode.getId())
                .entityType("EPISODE")
                .name(episode.getTitle())
                .description(episode.getSynopsis())
                .coverUrl(coverUrl)
                .version(episode.getVersion())
                .status(episode.getStatus())
                .updatedAt(episode.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 Storyboard 实体转换
     */
    public static EntityInfoResponse fromStoryboard(Storyboard storyboard, String coverUrl) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scriptId", storyboard.getScriptId());
        detail.put("episodeId", storyboard.getEpisodeId());
        detail.put("title", storyboard.getTitle());
        detail.put("sequence", storyboard.getSequence());
        detail.put("synopsis", storyboard.getSynopsis());
        detail.put("duration", storyboard.getDuration());
        detail.put("visualDesc", storyboard.getVisualDesc());
        detail.put("audioDesc", storyboard.getAudioDesc());
        detail.put("currentVersionId", storyboard.getCurrentVersionId());
        detail.put("versionNumber", storyboard.getVersionNumber());
        detail.put("extraInfo", storyboard.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(storyboard.getId())
                .entityType("STORYBOARD")
                .name(storyboard.getTitle())
                .description(storyboard.getSynopsis())
                .coverUrl(coverUrl)
                .version(storyboard.getVersion())
                .status(storyboard.getStatus())
                .updatedAt(storyboard.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 Asset 实体转换
     */
    public static EntityInfoResponse fromAsset(Asset asset) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scope", asset.getScope());
        detail.put("scriptId", asset.getScriptId());
        detail.put("assetType", asset.getAssetType());
        detail.put("source", asset.getSource());
        detail.put("mimeType", asset.getMimeType());
        detail.put("fileKey", asset.getFileKey());
        detail.put("fileUrl", asset.getFileUrl());
        detail.put("thumbnailUrl", asset.getThumbnailUrl());
        detail.put("fileSize", asset.getFileSize());
        detail.put("generationStatus", asset.getGenerationStatus());
        detail.put("workflowId", asset.getWorkflowId());
        detail.put("taskId", asset.getTaskId());
        detail.put("currentVersionId", asset.getCurrentVersionId());
        detail.put("versionNumber", asset.getVersionNumber());
        detail.put("metaInfo", asset.getMetaInfo());
        detail.put("extraInfo", asset.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(asset.getId())
                .entityType("ASSET")
                .name(asset.getName())
                .description(asset.getDescription())
                .coverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl())
                .version(asset.getVersion())
                .status(asset.getGenerationStatus())
                .updatedAt(asset.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 AssetResponse 转换（包含预签名 URL）
     * 用于内部接口返回带预签名 URL 的素材数据
     */
    public static EntityInfoResponse fromAssetResponse(AssetResponse assetResponse) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("scope", assetResponse.getScope());
        detail.put("scriptId", assetResponse.getScriptId());
        detail.put("assetType", assetResponse.getAssetType());
        detail.put("source", assetResponse.getSource());
        detail.put("mimeType", assetResponse.getMimeType());
        detail.put("fileKey", assetResponse.getFileKey());
        // 使用已预签名的 URL
        detail.put("fileUrl", assetResponse.getFileUrl());
        detail.put("thumbnailUrl", assetResponse.getThumbnailUrl());
        detail.put("fileSize", assetResponse.getFileSize());
        detail.put("generationStatus", assetResponse.getGenerationStatus());
        detail.put("workflowId", assetResponse.getWorkflowId());
        detail.put("taskId", assetResponse.getTaskId());
        detail.put("currentVersionId", assetResponse.getCurrentVersionId());
        detail.put("versionNumber", assetResponse.getVersionNumber());
        detail.put("metaInfo", assetResponse.getMetaInfo());
        detail.put("extraInfo", assetResponse.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(assetResponse.getId())
                .entityType("ASSET")
                .name(assetResponse.getName())
                .description(assetResponse.getDescription())
                .coverUrl(assetResponse.getThumbnailUrl() != null ? assetResponse.getThumbnailUrl() : assetResponse.getFileUrl())
                .version(assetResponse.getVersionNumber())
                .status(assetResponse.getGenerationStatus())
                .updatedAt(assetResponse.getUpdatedAt())
                .detail(detail)
                .build();
    }

    /**
     * 从 Script 实体转换
     */
    public static EntityInfoResponse fromScript(Script script, String coverUrl) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("title", script.getTitle());
        detail.put("synopsis", script.getSynopsis());
        detail.put("content", script.getContent());
        detail.put("coverAssetId", script.getCoverAssetId());
        detail.put("docAssetId", script.getDocAssetId());
        detail.put("currentVersionId", script.getCurrentVersionId());
        detail.put("versionNumber", script.getVersionNumber());
        detail.put("extraInfo", script.getExtraInfo());

        return EntityInfoResponse.builder()
                .id(script.getId())
                .entityType("SCRIPT")
                .name(script.getTitle())
                .description(script.getSynopsis())
                .coverUrl(coverUrl)
                .version(script.getVersionNumber() != null ? script.getVersionNumber() : 1)
                .status(script.getStatus())
                .updatedAt(script.getUpdatedAt())
                .detail(detail)
                .build();
    }
}
