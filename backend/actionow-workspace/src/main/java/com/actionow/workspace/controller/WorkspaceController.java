package com.actionow.workspace.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.workspace.dto.*;
import com.actionow.workspace.service.WorkspaceInvitationService;
import com.actionow.workspace.service.WorkspaceMemberService;
import com.actionow.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作空间控制器
 * 工作空间相关操作的workspaceId从请求头获取（通过 UserContextHolder）
 * 创建和列表操作不需要workspaceId
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService memberService;
    private final WorkspaceInvitationService invitationService;

    /**
     * 创建工作空间
     */
    @PostMapping
    @RequireLogin
    public Result<WorkspaceResponse> create(@RequestBody @Valid CreateWorkspaceRequest request) {
        String userId = UserContextHolder.getUserId();
        WorkspaceResponse response = workspaceService.create(request, userId);
        return Result.success(response);
    }

    /**
     * 更新工作空间
     */
    @PatchMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<WorkspaceResponse> update(@RequestBody @Valid UpdateWorkspaceRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        WorkspaceResponse response = workspaceService.update(workspaceId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除工作空间
     */
    @DeleteMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.CREATOR)
    public Result<Void> delete() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        workspaceService.delete(workspaceId, userId);
        return Result.success();
    }

    /**
     * 获取工作空间详情
     */
    @GetMapping("/current")
    @RequireWorkspaceMember(minRole = WorkspaceRole.GUEST)
    public Result<WorkspaceResponse> getCurrent() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        WorkspaceResponse response = workspaceService.getById(workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 获取当前用户的工作空间列表
     */
    @GetMapping
    @RequireLogin
    public Result<List<WorkspaceResponse>> listMyWorkspaces() {
        String userId = UserContextHolder.getUserId();
        List<WorkspaceResponse> workspaces = workspaceService.listByUser(userId);
        return Result.success(workspaces);
    }

    /**
     * 转让工作空间
     */
    @PostMapping("/transfer")
    @RequireWorkspaceMember(minRole = WorkspaceRole.CREATOR)
    public Result<Void> transferOwnership(@RequestParam String newOwnerId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        workspaceService.transferOwnership(workspaceId, newOwnerId, userId);
        return Result.success();
    }

    /**
     * 变更订阅计划（Creator 专属）
     */
    @PostMapping("/plan")
    @RequireWorkspaceMember(minRole = WorkspaceRole.CREATOR)
    public Result<Void> updatePlan(@RequestParam String planType) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        workspaceService.updatePlan(workspaceId, planType, userId);
        return Result.success();
    }

    // ==================== 成员管理 ====================

    /**
     * 获取成员列表（分页）
     */
    @GetMapping("/members")
    @RequireWorkspaceMember(minRole = WorkspaceRole.GUEST)
    public Result<PageResult<WorkspaceMemberResponse>> listMembers(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String role) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        PageResult<WorkspaceMemberResponse> members = memberService.listMembersPage(workspaceId, current, size, role);
        return Result.success(members);
    }

    /**
     * 移除成员
     */
    @DeleteMapping("/members/{memberId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> removeMember(@PathVariable String memberId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        memberService.removeMemberById(workspaceId, memberId, operatorId);
        return Result.success();
    }

    /**
     * 退出工作空间
     */
    @PostMapping("/leave")
    @RequireWorkspaceMember(minRole = WorkspaceRole.GUEST)
    public Result<Void> leave() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        memberService.leaveWorkspace(workspaceId, userId);
        return Result.success();
    }

    /**
     * 更新成员角色
     */
    @PatchMapping("/members/{memberId}/role")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> updateMemberRole(
            @PathVariable String memberId,
            @RequestBody @Valid UpdateMemberRoleRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        memberService.updateMemberRoleById(workspaceId, memberId, request.getRole(), operatorId);
        return Result.success();
    }

    // ==================== 设置管理 ====================

    /**
     * 切换「普通成员是否可创建剧本」开关
     * 仅 workspace ADMIN+ 可操作；CREATOR/ADMIN 的创建权限不受此开关影响
     */
    @PatchMapping("/settings/script-creation")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> updateScriptCreationSetting(@RequestParam boolean enabled) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        workspaceService.updateScriptCreationSetting(workspaceId, enabled, userId);
        return Result.success();
    }

    // ==================== 邀请管理 ====================

    /**
     * 创建邀请
     */
    @PostMapping("/invitations")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<InvitationResponse> createInvitation(@RequestBody @Valid CreateInvitationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String inviterId = UserContextHolder.getUserId();
        InvitationResponse response = invitationService.createInvitation(workspaceId, request, inviterId);
        return Result.success(response);
    }

    /**
     * 获取邀请列表（分页）
     */
    @GetMapping("/invitations")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<PageResult<InvitationResponse>> listInvitations(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        PageResult<InvitationResponse> invitations = invitationService.listInvitationsPage(workspaceId, operatorId, current, size);
        return Result.success(invitations);
    }

    /**
     * 禁用邀请
     */
    @DeleteMapping("/invitations/{invitationId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> disableInvitation(@PathVariable String invitationId) {
        String operatorId = UserContextHolder.getUserId();
        invitationService.disableInvitation(invitationId, operatorId);
        return Result.success();
    }

    /**
     * 获取邀请详情（公开接口，用于展示邀请信息）
     */
    @GetMapping("/invite/{code}")
    @IgnoreAuth
    public Result<InvitationResponse> getInvitationByCode(@PathVariable String code) {
        InvitationResponse response = invitationService.getInvitationByCode(code);
        return Result.success(response);
    }

    /**
     * 接受邀请
     */
    @PostMapping("/invite/{code}/accept")
    @RequireLogin
    public Result<WorkspaceMemberResponse> acceptInvitation(@PathVariable String code) {
        String userId = UserContextHolder.getUserId();
        String email = UserContextHolder.getEmail();
        WorkspaceMemberResponse response = invitationService.acceptInvitation(code, userId, email);
        return Result.success(response);
    }
}
