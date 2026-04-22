package com.actionow.workspace.service;

import com.actionow.common.core.result.PageResult;
import com.actionow.workspace.dto.WorkspaceMemberResponse;
import com.actionow.workspace.entity.WorkspaceMember;

import java.util.List;
import java.util.Optional;

/**
 * 工作空间成员服务接口
 *
 * @author Actionow
 */
public interface WorkspaceMemberService {

    /**
     * 添加成员
     *
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @param role        角色
     * @param invitedBy   邀请人ID
     * @return 成员信息
     */
    WorkspaceMember addMember(String workspaceId, String userId, String role, String invitedBy);

    /**
     * 移除成员
     *
     * @param workspaceId 工作空间ID
     * @param userId      被移除的用户ID
     * @param operatorId  操作者ID
     */
    void removeMember(String workspaceId, String userId, String operatorId);

    /**
     * 根据成员记录ID移除成员
     *
     * @param workspaceId 工作空间ID
     * @param memberId    成员记录ID
     * @param operatorId  操作者ID
     */
    void removeMemberById(String workspaceId, String memberId, String operatorId);

    /**
     * 成员主动退出
     *
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     */
    void leaveWorkspace(String workspaceId, String userId);

    /**
     * 更新成员角色
     *
     * @param workspaceId 工作空间ID
     * @param userId      目标用户ID
     * @param newRole     新角色
     * @param operatorId  操作者ID
     */
    void updateMemberRole(String workspaceId, String userId, String newRole, String operatorId);

    /**
     * 根据成员记录ID更新成员角色
     *
     * @param workspaceId 工作空间ID
     * @param memberId    成员记录ID
     * @param newRole     新角色
     * @param operatorId  操作者ID
     */
    void updateMemberRoleById(String workspaceId, String memberId, String newRole, String operatorId);

    /**
     * 获取工作空间成员列表
     *
     * @param workspaceId 工作空间ID
     * @return 成员列表
     */
    List<WorkspaceMemberResponse> listMembers(String workspaceId);

    /**
     * 分页获取工作空间成员列表
     *
     * @param workspaceId 工作空间ID
     * @param current     当前页码
     * @param size        每页大小
     * @param role        角色筛选（可选）
     * @return 分页成员列表
     */
    PageResult<WorkspaceMemberResponse> listMembersPage(String workspaceId, Long current, Long size, String role);

    /**
     * 获取用户在工作空间的成员信息
     *
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 成员信息
     */
    Optional<WorkspaceMember> getMember(String workspaceId, String userId);

    /**
     * 检查用户是否是工作空间成员
     *
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 是否是成员
     */
    boolean isMember(String workspaceId, String userId);

    /**
     * 统计工作空间成员数量
     *
     * @param workspaceId 工作空间ID
     * @return 成员数量
     */
    int countMembers(String workspaceId);

    /**
     * 获取用户加入的所有工作空间的成员记录
     *
     * @param userId 用户ID
     * @return 成员记录列表
     */
    List<WorkspaceMember> getMembershipsByUser(String userId);
}
