package com.actionow.project.dto;

import com.actionow.project.entity.Character;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 角色详情响应 - 包含完整字段（含 appearanceData, extraInfo）
 *
 * @author Actionow
 */
@Data
public class CharacterDetailResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String scriptId;
    private String name;
    private String description;
    private String fixedDesc;
    private Integer age;
    private String gender;
    private String characterType;
    private String voiceSeedId;
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

    public static CharacterDetailResponse fromEntity(Character character) {
        CharacterDetailResponse response = new CharacterDetailResponse();
        response.setId(character.getId());
        response.setWorkspaceId(character.getWorkspaceId());
        response.setScope(character.getScope());
        response.setScriptId(character.getScriptId());
        response.setName(character.getName());
        response.setDescription(character.getDescription());
        response.setFixedDesc(character.getFixedDesc());
        response.setAge(character.getAge());
        response.setGender(character.getGender());
        response.setCharacterType(character.getCharacterType());
        response.setVoiceSeedId(character.getVoiceSeedId());
        response.setAppearanceData(character.getAppearanceData());
        response.setCoverAssetId(character.getCoverAssetId());
        response.setCurrentVersionId(character.getCurrentVersionId());
        response.setVersionNumber(character.getVersionNumber());
        response.setCreatedAt(character.getCreatedAt());
        response.setUpdatedAt(character.getUpdatedAt());
        response.setCreatedBy(character.getCreatedBy());
        response.setExtraInfo(character.getExtraInfo());
        return response;
    }
}
