package com.actionow.workspace.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作空间邀请实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workspace_invitation")
public class WorkspaceInvitation extends BaseEntity {

    /**
     * 工作空间ID
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 邀请码
     */
    private String code;

    /**
     * 邀请人ID
     */
    @TableField("inviter_id")
    private String inviterId;

    /**
     * 被邀请人邮箱（可选）
     */
    @TableField("invitee_email")
    private String inviteeEmail;

    /**
     * 分配的角色
     */
    private String role;

    /**
     * 过期时间
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * 最大使用次数
     */
    @TableField("max_uses")
    private Integer maxUses;

    /**
     * 已使用次数
     */
    @TableField("used_count")
    private Integer usedCount;

    /**
     * 状态: ACTIVE-有效, DISABLED-已禁用
     */
    private String status;
}
