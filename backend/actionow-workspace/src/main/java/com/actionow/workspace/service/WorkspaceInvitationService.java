package com.actionow.workspace.service;

import com.actionow.common.core.result.PageResult;
import com.actionow.workspace.dto.CreateInvitationRequest;
import com.actionow.workspace.dto.InvitationResponse;
import com.actionow.workspace.dto.WorkspaceMemberResponse;

import java.util.List;

/**
 * 工作空间邀请服务接口
 *
 * @author Actionow
 */
public interface WorkspaceInvitationService {

    /**
     * 创建邀请
     *
     * @param workspaceId 工作空间ID
     * @param request     邀请请求
     * @param inviterId   邀请人ID
     * @return 邀请响应
     */
    InvitationResponse createInvitation(String workspaceId, CreateInvitationRequest request, String inviterId);

    /**
     * 接受邀请
     *
     * @param code   邀请码
     * @param userId 接受邀请的用户ID
     * @param email  用户邮箱（用于校验指定邮箱的邀请）
     * @return 成员信息
     */
    WorkspaceMemberResponse acceptInvitation(String code, String userId, String email);

    /**
     * 获取邀请详情（不验证用户权限，用于展示邀请信息）
     *
     * @param code 邀请码
     * @return 邀请响应
     */
    InvitationResponse getInvitationByCode(String code);

    /**
     * 获取工作空间的邀请列表
     *
     * @param workspaceId 工作空间ID
     * @param operatorId  操作者ID
     * @return 邀请列表
     */
    List<InvitationResponse> listInvitations(String workspaceId, String operatorId);

    /**
     * 分页获取工作空间的邀请列表
     *
     * @param workspaceId 工作空间ID
     * @param operatorId  操作者ID
     * @param current     当前页
     * @param size        每页大小
     * @return 邀请分页列表
     */
    PageResult<InvitationResponse> listInvitationsPage(String workspaceId, String operatorId, Long current, Long size);

    /**
     * 禁用邀请
     *
     * @param invitationId 邀请ID
     * @param operatorId   操作者ID
     */
    void disableInvitation(String invitationId, String operatorId);
}
