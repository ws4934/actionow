package com.actionow.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Refresh Token轮换记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_refresh_token")
public class RefreshTokenRecord extends BaseEntity {

    @TableField("session_id")
    private String sessionId;

    @TableField("token_jti")
    private String tokenJti;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("family_id")
    private String familyId;

    @TableField("parent_token_jti")
    private String parentTokenJti;

    @TableField("replaced_by_jti")
    private String replacedByJti;

    private String status;

    @TableField("issued_at")
    private LocalDateTime issuedAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("reuse_detected")
    private Boolean reuseDetected;

    private String reason;
}

