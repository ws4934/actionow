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
 * 用户实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_user", autoResultMap = true)
public class User extends BaseEntity {

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 邮箱（唯一）
     */
    private String email;

    /**
     * 手机号（唯一）
     */
    private String phone;

    /**
     * 密码（BCrypt加密）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 用户状态：Active-正常，Banned-已禁用，Inactive-未激活
     */
    private String status;

    /**
     * 邮箱是否已验证
     */
    @TableField("email_verified")
    private Boolean emailVerified;

    /**
     * 手机是否已验证
     */
    @TableField("phone_verified")
    private Boolean phoneVerified;

    /**
     * 最后登录时间
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 最后登录IP
     */
    @TableField("last_login_ip")
    private String lastLoginIp;

    /**
     * 登录失败次数
     */
    @TableField("login_fail_count")
    private Integer loginFailCount;

    /**
     * 账号锁定截止时间
     */
    @TableField("locked_until")
    private LocalDateTime lockedUntil;

    /**
     * 扩展信息（JSON格式）
     * 包含：bio, location, company, website, preferences等
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;

    /**
     * 邀请人用户ID
     */
    @TableField("invited_by")
    private String invitedBy;

    /**
     * 注册时使用的邀请码
     */
    @TableField("invitation_code_used")
    private String invitationCodeUsed;
}
