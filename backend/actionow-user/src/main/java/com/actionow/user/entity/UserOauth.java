package com.actionow.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OAuth 第三方账号绑定实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_user_oauth", autoResultMap = true)
public class UserOauth extends BaseEntity {

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * OAuth 提供商: github, google, wechat, apple
     */
    private String provider;

    /**
     * OAuth 提供商的用户ID (OpenID)
     */
    @TableField("provider_user_id")
    private String providerUserId;

    /**
     * OAuth UnionID（微信等平台使用）
     */
    @TableField("union_id")
    private String unionId;

    /**
     * OAuth 提供商的用户名
     */
    @TableField("provider_username")
    private String providerUsername;

    /**
     * OAuth 提供商的邮箱
     */
    @TableField("provider_email")
    private String providerEmail;

    /**
     * OAuth 提供商的头像
     */
    @TableField("provider_avatar")
    private String providerAvatar;

    /**
     * Access Token（加密存储）
     */
    @TableField("access_token")
    private String accessToken;

    /**
     * Refresh Token（加密存储）
     */
    @TableField("refresh_token")
    private String refreshToken;

    /**
     * Token 有效期（秒）
     */
    @TableField("expires_in")
    private Integer expiresIn;

    /**
     * Token 过期时间
     */
    @TableField("token_expires_at")
    private LocalDateTime tokenExpiresAt;

    /**
     * 第三方返回的原始信息（JSON格式）
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;
}
