package com.actionow.project.dto;

import com.actionow.project.entity.ScriptPermission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剧本权限响应 DTO
 *
 * @author Actionow
 */
@Data
public class ScriptPermissionResponse {

    private String id;
    private String scriptId;
    private String userId;
    private String permissionType;
    private String grantSource;
    private String grantedBy;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;

    /** 被授权用户名（富化字段） */
    private String username;
    /** 被授权用户昵称（富化字段） */
    private String nickname;
    /** 被授权用户头像（富化字段） */
    private String avatar;

    public static ScriptPermissionResponse fromEntity(ScriptPermission entity) {
        ScriptPermissionResponse dto = new ScriptPermissionResponse();
        dto.setId(entity.getId());
        dto.setScriptId(entity.getScriptId());
        dto.setUserId(entity.getUserId());
        dto.setPermissionType(entity.getPermissionType());
        dto.setGrantSource(entity.getGrantSource());
        dto.setGrantedBy(entity.getGrantedBy());
        dto.setGrantedAt(entity.getGrantedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        return dto;
    }
}
