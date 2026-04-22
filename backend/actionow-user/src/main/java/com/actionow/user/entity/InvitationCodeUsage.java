package com.actionow.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邀请码使用记录实体
 *
 * @author Actionow
 */
@Data
@TableName("t_invitation_code_usage")
public class InvitationCodeUsage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（UUIDv7）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 邀请码ID
     */
    @TableField("invitation_code_id")
    private String invitationCodeId;

    /**
     * 邀请码（冗余字段）
     */
    private String code;

    /**
     * 邀请人用户ID
     */
    @TableField("inviter_id")
    private String inviterId;

    /**
     * 被邀请人用户ID
     */
    @TableField("invitee_id")
    private String inviteeId;

    /**
     * 被邀请人用户名
     */
    @TableField("invitee_username")
    private String inviteeUsername;

    /**
     * 被邀请人邮箱
     */
    @TableField("invitee_email")
    private String inviteeEmail;

    /**
     * 使用时间
     */
    @TableField("used_at")
    private LocalDateTime usedAt;

    /**
     * IP地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 浏览器UA
     */
    @TableField("user_agent")
    private String userAgent;
}
