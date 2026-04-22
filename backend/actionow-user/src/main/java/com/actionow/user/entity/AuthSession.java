package com.actionow.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 认证会话实体（SID）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_auth_session")
public class AuthSession extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("workspace_id")
    private String workspaceId;

    @TableField("tenant_schema")
    private String tenantSchema;

    private String status;

    @TableField("perm_version")
    private Integer permVersion;

    @TableField("device_id")
    private String deviceId;

    @TableField("user_agent")
    private String userAgent;

    @TableField("last_ip")
    private String lastIp;

    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;
}

