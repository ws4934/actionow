package com.actionow.project.dto;

import com.actionow.project.entity.Prop;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 道具详情响应 - 包含完整字段（含 appearanceData, extraInfo）
 *
 * @author Actionow
 */
@Data
public class PropDetailResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String scriptId;
    private String name;
    private String description;
    private String fixedDesc;
    private String propType;
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

    public static PropDetailResponse fromEntity(Prop prop) {
        PropDetailResponse response = new PropDetailResponse();
        response.setId(prop.getId());
        response.setWorkspaceId(prop.getWorkspaceId());
        response.setScope(prop.getScope());
        response.setScriptId(prop.getScriptId());
        response.setName(prop.getName());
        response.setDescription(prop.getDescription());
        response.setFixedDesc(prop.getFixedDesc());
        response.setPropType(prop.getPropType());
        response.setAppearanceData(prop.getAppearanceData());
        response.setCoverAssetId(prop.getCoverAssetId());
        response.setCurrentVersionId(prop.getCurrentVersionId());
        response.setVersionNumber(prop.getVersionNumber());
        response.setCreatedAt(prop.getCreatedAt());
        response.setUpdatedAt(prop.getUpdatedAt());
        response.setCreatedBy(prop.getCreatedBy());
        response.setExtraInfo(prop.getExtraInfo());
        return response;
    }
}
