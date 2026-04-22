package com.actionow.project.dto;

import com.actionow.project.entity.Prop;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 道具列表响应 - 只包含列表渲染所需字段，不含 appearanceData
 *
 * @author Actionow
 */
@Data
public class PropListResponse {

    private String id;
    private String workspaceId;
    private String scope;
    private String scriptId;
    private String name;
    private String description;
    private String propType;
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

    public static PropListResponse fromEntity(Prop prop) {
        PropListResponse response = new PropListResponse();
        response.setId(prop.getId());
        response.setWorkspaceId(prop.getWorkspaceId());
        response.setScope(prop.getScope());
        response.setScriptId(prop.getScriptId());
        response.setName(prop.getName());
        response.setDescription(prop.getDescription());
        response.setPropType(prop.getPropType());
        response.setCoverAssetId(prop.getCoverAssetId());
        response.setVersionNumber(prop.getVersionNumber());
        response.setCreatedAt(prop.getCreatedAt());
        response.setUpdatedAt(prop.getUpdatedAt());
        response.setCreatedBy(prop.getCreatedBy());
        return response;
    }
}
