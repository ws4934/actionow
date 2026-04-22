package com.actionow.workspace.dto;

import com.actionow.workspace.entity.WorkspaceInvitation;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请响应
 *
 * @author Actionow
 */
@Data
public class InvitationResponse {

    /**
     * 邀请ID
     */
    private String id;

    /**
     * 邀请码
     */
    private String code;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 邀请人ID
     */
    private String inviterId;

    /**
     * 被邀请人邮箱
     */
    private String inviteeEmail;

    /**
     * 分配的角色
     */
    private String role;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 最大使用次数
     */
    private Integer maxUses;

    /**
     * 已使用次数
     */
    private Integer usedCount;

    /**
     * 状态: ACTIVE-有效, DISABLED-已禁用
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 邀请链接（前端拼接基础URL）
     */
    private String inviteLink;

    /**
     * 从实体转换
     */
    public static InvitationResponse fromEntity(WorkspaceInvitation invitation) {
        InvitationResponse response = new InvitationResponse();
        response.setId(invitation.getId());
        response.setCode(invitation.getCode());
        response.setWorkspaceId(invitation.getWorkspaceId());
        response.setInviterId(invitation.getInviterId());
        response.setInviteeEmail(invitation.getInviteeEmail());
        response.setRole(invitation.getRole());
        response.setExpiresAt(invitation.getExpiresAt());
        response.setMaxUses(invitation.getMaxUses());
        response.setUsedCount(invitation.getUsedCount());
        response.setStatus(invitation.getStatus());
        response.setCreatedAt(invitation.getCreatedAt());
        response.setInviteLink("/invite/" + invitation.getCode());
        return response;
    }
}
