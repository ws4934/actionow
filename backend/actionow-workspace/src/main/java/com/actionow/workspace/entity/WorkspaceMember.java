package com.actionow.workspace.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作空间成员实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workspace_member")
public class WorkspaceMember extends BaseEntity {

    /**
     * 工作空间ID
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 成员角色: Creator, Admin, Member, Guest
     */
    private String role;

    /**
     * 成员状态: Active, Inactive, Invited
     */
    private String status;

    /**
     * 成员昵称（在工作空间内的昵称）
     */
    private String nickname;

    /**
     * 邀请人ID
     */
    @TableField("invited_by")
    private String invitedBy;

    /**
     * 加入时间
     */
    @TableField("joined_at")
    private LocalDateTime joinedAt;
}
