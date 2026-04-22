package com.actionow.workspace.dto;

import com.actionow.workspace.entity.WorkspaceMember;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间成员响应
 *
 * @author Actionow
 */
@Data
public class WorkspaceMemberResponse {

    /**
     * 成员记录ID
     */
    private String id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 成员角色: Creator, Admin, Member, Guest
     */
    private String role;

    /**
     * 成员状态: Active, Inactive, Invited
     */
    private String status;

    /**
     * 成员昵称
     */
    private String nickname;

    /**
     * 邀请人ID
     */
    private String invitedBy;

    /**
     * 加入时间
     */
    private LocalDateTime joinedAt;

    /**
     * 从实体转换（用户信息需额外查询填充）
     */
    public static WorkspaceMemberResponse fromEntity(WorkspaceMember member) {
        WorkspaceMemberResponse response = new WorkspaceMemberResponse();
        response.setId(member.getId());
        response.setUserId(member.getUserId());
        response.setRole(member.getRole());
        response.setStatus(member.getStatus());
        response.setNickname(member.getNickname());
        response.setInvitedBy(member.getInvitedBy());
        response.setJoinedAt(member.getJoinedAt());
        return response;
    }
}
