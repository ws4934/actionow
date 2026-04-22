package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 剧本权限实体
 * 存储剧本级别的细粒度权限记录，位于租户 schema 内
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_script_permission")
public class ScriptPermission extends TenantBaseEntity {

    /**
     * 剧本ID
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 被授权的用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 权限类型: VIEW | EDIT | ADMIN
     */
    @TableField("permission_type")
    private String permissionType;

    /**
     * 授权来源: WORKSPACE_ADMIN | SCRIPT_OWNER
     */
    @TableField("grant_source")
    private String grantSource;

    /**
     * 授权人ID
     */
    @TableField("granted_by")
    private String grantedBy;

    /**
     * 授权时间
     */
    @TableField("granted_at")
    private LocalDateTime grantedAt;

    /**
     * 权限过期时间（null 表示永不过期）
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;
}
