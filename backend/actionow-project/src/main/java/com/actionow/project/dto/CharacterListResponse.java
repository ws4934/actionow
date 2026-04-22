package com.actionow.project.dto;

import com.actionow.project.entity.Character;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色列表响应 - 只包含列表渲染所需字段，不含 appearanceData
 *
 * @author Actionow
 */
@Data
public class CharacterListResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String scriptId;
    private String name;
    private String description;
    private Integer age;
    private String gender;
    private String characterType;
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

    public static CharacterListResponse fromEntity(Character character) {
        CharacterListResponse response = new CharacterListResponse();
        response.setId(character.getId());
        response.setWorkspaceId(character.getWorkspaceId());
        response.setScope(character.getScope());
        response.setScriptId(character.getScriptId());
        response.setName(character.getName());
        response.setDescription(character.getDescription());
        response.setAge(character.getAge());
        response.setGender(character.getGender());
        response.setCharacterType(character.getCharacterType());
        response.setCoverAssetId(character.getCoverAssetId());
        response.setVersionNumber(character.getVersionNumber());
        response.setCreatedAt(character.getCreatedAt());
        response.setUpdatedAt(character.getUpdatedAt());
        response.setCreatedBy(character.getCreatedBy());
        return response;
    }
}
