package com.actionow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剧本创建者邀请协作者请求
 * 若被邀请者尚未加入工作空间，自动以 GUEST 角色加入
 *
 * @author Actionow
 */
@Data
public class InviteScriptCollaboratorRequest {

    /**
     * 被邀请的用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * 赋予的权限类型: VIEW | EDIT | ADMIN
     */
    @NotBlank(message = "权限类型不能为空")
    @Pattern(regexp = "VIEW|EDIT|ADMIN", message = "权限类型必须为 VIEW、EDIT 或 ADMIN")
    private String permissionType;

    /**
     * 权限过期时间（null 表示永不过期）
     */
    private LocalDateTime expiresAt;
}
