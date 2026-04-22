package com.actionow.project.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.GrantScriptPermissionRequest;
import com.actionow.project.dto.InviteScriptCollaboratorRequest;
import com.actionow.project.dto.ScriptPermissionResponse;
import com.actionow.project.service.ScriptPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 剧本权限控制器
 * 提供剧本维度的细粒度权限管理接口
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/scripts")
@RequiredArgsConstructor
public class ScriptPermissionController {

    private final ScriptPermissionService scriptPermissionService;

    // ==================== 管理员维度（需 workspace ADMIN+） ====================

    /**
     * 授权用户访问剧本
     */
    @PostMapping("/{scriptId}/permissions")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<ScriptPermissionResponse> grantPermission(
            @PathVariable String scriptId,
            @RequestBody @Valid GrantScriptPermissionRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        ScriptPermissionResponse response = scriptPermissionService.grantPermission(scriptId, request, workspaceId, operatorId);
        return Result.success(response);
    }

    /**
     * 撤销用户的剧本访问权限
     */
    @DeleteMapping("/{scriptId}/permissions/{userId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> revokePermission(
            @PathVariable String scriptId,
            @PathVariable String userId) {
        String operatorId = UserContextHolder.getUserId();
        scriptPermissionService.revokePermission(scriptId, userId, operatorId);
        return Result.success();
    }

    /**
     * 查看剧本的权限列表
     */
    @GetMapping("/{scriptId}/permissions")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<List<ScriptPermissionResponse>> listPermissions(@PathVariable String scriptId) {
        List<ScriptPermissionResponse> permissions = scriptPermissionService.listPermissions(scriptId);
        return Result.success(permissions);
    }

    // ==================== 剧本创建者维度（需 script ADMIN 或 workspace ADMIN+） ====================

    /**
     * 邀请协作者（被邀请者若非工作空间成员将自动加入为 GUEST）
     */
    @PostMapping("/{scriptId}/collaborators")
    @RequireWorkspaceMember
    public Result<ScriptPermissionResponse> inviteCollaborator(
            @PathVariable String scriptId,
            @RequestBody @Valid InviteScriptCollaboratorRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();
        ScriptPermissionResponse response = scriptPermissionService.inviteCollaborator(scriptId, request, workspaceId, operatorId);
        return Result.success(response);
    }

    /**
     * 移除协作者
     */
    @DeleteMapping("/{scriptId}/collaborators/{userId}")
    @RequireWorkspaceMember
    public Result<Void> removeCollaborator(
            @PathVariable String scriptId,
            @PathVariable String userId) {
        String operatorId = UserContextHolder.getUserId();
        scriptPermissionService.removeCollaborator(scriptId, userId, operatorId);
        return Result.success();
    }
}
