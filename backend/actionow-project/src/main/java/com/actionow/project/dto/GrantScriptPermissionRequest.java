package com.actionow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员授权剧本访问请求
 *
 * @author Actionow
 */
@Data
public class GrantScriptPermissionRequest {

    /**
     * 被授权用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * 权限类型: VIEW | EDIT | ADMIN
     */
    @NotBlank(message = "权限类型不能为空")
    @Pattern(regexp = "VIEW|EDIT|ADMIN", message = "权限类型必须为 VIEW、EDIT 或 ADMIN")
    private String permissionType;

    /**
     * 权限过期时间（null 表示永不过期）
     */
    private LocalDateTime expiresAt;
}
