package com.actionow.project.service.impl;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.workspace.WorkspaceMembershipInfo;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.dto.GrantScriptPermissionRequest;
import com.actionow.project.dto.InviteScriptCollaboratorRequest;
import com.actionow.project.dto.ScriptPermissionResponse;
import com.actionow.project.entity.ScriptPermission;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.project.feign.WorkspaceFeignClient;
import com.actionow.project.mapper.ScriptPermissionMapper;
import com.actionow.project.service.ScriptPermissionService;
import com.actionow.project.service.UserInfoHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 剧本权限服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptPermissionServiceImpl implements ScriptPermissionService {

    private final ScriptPermissionMapper permissionMapper;
    private final WorkspaceFeignClient workspaceFeignClient;
    private final UserInfoHelper userInfoHelper;

    // ==================== 管理员维度操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScriptPermissionResponse grantPermission(String scriptId, GrantScriptPermissionRequest request,
                                                     String workspaceId, String operatorId) {
        requireWorkspaceAdmin();

        // 检查权限是否已存在
        ScriptPermission existing = permissionMapper.selectByScriptAndUser(scriptId, request.getUserId());
        if (existing != null) {
            throw new BusinessException(ResultCode.SCRIPT_PERMISSION_EXISTS);
        }

        ScriptPermission permission = buildPermission(scriptId, workspaceId, request.getUserId(),
                request.getPermissionType(), ProjectConstants.GrantSource.WORKSPACE_ADMIN,
                operatorId, request.getExpiresAt());
        permissionMapper.insert(permission);

        log.info("剧本权限授权: scriptId={}, userId={}, type={}, grantedBy={}",
                scriptId, request.getUserId(), request.getPermissionType(), operatorId);
        return enrichResponse(ScriptPermissionResponse.fromEntity(permission));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokePermission(String scriptId, String userId, String operatorId) {
        requireWorkspaceAdmin();

        ScriptPermission permission = permissionMapper.selectByScriptAndUser(scriptId, userId);
        if (permission == null) {
            throw new BusinessException(ResultCode.SCRIPT_PERMISSION_NOT_FOUND);
        }
        if (ProjectConstants.GrantSource.SCRIPT_OWNER.equals(permission.getGrantSource())) {
            throw new BusinessException(ResultCode.CANNOT_REMOVE_SCRIPT_OWNER);
        }

        permissionMapper.deleteById(permission.getId());
        log.info("剧本权限撤销: scriptId={}, userId={}, operatorId={}", scriptId, userId, operatorId);
    }

    @Override
    public List<ScriptPermissionResponse> listPermissions(String scriptId) {
        List<ScriptPermission> permissions = permissionMapper.selectByScriptId(scriptId);
        if (permissions.isEmpty()) {
            return List.of();
        }

        List<ScriptPermissionResponse> responses = permissions.stream()
                .map(ScriptPermissionResponse::fromEntity)
                .collect(Collectors.toList());

        // 批量富化用户信息
        Set<String> userIds = permissions.stream()
                .map(ScriptPermission::getUserId)
                .collect(Collectors.toSet());
        Map<String, UserBasicInfo> userInfoMap = userInfoHelper.batchGetUserInfo(userIds);

        responses.forEach(resp -> {
            UserBasicInfo info = userInfoMap.get(resp.getUserId());
            if (info != null) {
                resp.setUsername(info.getUsername());
                resp.setNickname(info.getNickname());
                resp.setAvatar(info.getAvatar());
            }
        });

        return responses;
    }

    // ==================== 创建者维度操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScriptPermissionResponse inviteCollaborator(String scriptId, InviteScriptCollaboratorRequest request,
                                                       String workspaceId, String operatorId) {
        requireScriptAdminOrWorkspaceAdmin(scriptId, operatorId);

        // 检查权限是否已存在
        ScriptPermission existing = permissionMapper.selectByScriptAndUser(scriptId, request.getUserId());
        if (existing != null) {
            throw new BusinessException(ResultCode.SCRIPT_PERMISSION_EXISTS);
        }

        // 检查被邀请者是否已是工作空间成员
        boolean isMember = isWorkspaceMember(workspaceId, request.getUserId());
        if (!isMember) {
            // 自动以 GUEST 角色加入工作空间
            addGuestMember(workspaceId, request.getUserId(), operatorId);
        }

        ScriptPermission permission = buildPermission(scriptId, workspaceId, request.getUserId(),
                request.getPermissionType(), ProjectConstants.GrantSource.SCRIPT_OWNER,
                operatorId, request.getExpiresAt());
        permissionMapper.insert(permission);

        log.info("剧本协作者邀请: scriptId={}, invitedUserId={}, type={}, invitedBy={}",
                scriptId, request.getUserId(), request.getPermissionType(), operatorId);
        return enrichResponse(ScriptPermissionResponse.fromEntity(permission));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCollaborator(String scriptId, String userId, String operatorId) {
        requireScriptAdminOrWorkspaceAdmin(scriptId, operatorId);

        ScriptPermission permission = permissionMapper.selectByScriptAndUser(scriptId, userId);
        if (permission == null) {
            throw new BusinessException(ResultCode.SCRIPT_PERMISSION_NOT_FOUND);
        }
        if (ProjectConstants.GrantSource.SCRIPT_OWNER.equals(permission.getGrantSource())) {
            throw new BusinessException(ResultCode.CANNOT_REMOVE_SCRIPT_OWNER);
        }

        permissionMapper.deleteById(permission.getId());
        log.info("剧本协作者移除: scriptId={}, userId={}, operatorId={}", scriptId, userId, operatorId);
    }

    // ==================== 内部工具方法 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOwnerPermission(String scriptId, String workspaceId, String userId) {
        ScriptPermission permission = buildPermission(scriptId, workspaceId, userId,
                ProjectConstants.ScriptPermissionType.ADMIN, ProjectConstants.GrantSource.SCRIPT_OWNER,
                userId, null);
        permissionMapper.insert(permission);
        log.debug("创建者权限记录创建: scriptId={}, userId={}", scriptId, userId);
    }

    @Override
    public boolean hasViewPermission(String scriptId, String userId) {
        String type = permissionMapper.selectPermissionType(scriptId, userId);
        return ProjectConstants.ScriptPermissionType.canView(type);
    }

    @Override
    public boolean hasEditPermission(String scriptId, String userId) {
        String type = permissionMapper.selectPermissionType(scriptId, userId);
        return ProjectConstants.ScriptPermissionType.canEdit(type);
    }

    @Override
    public boolean hasAdminPermission(String scriptId, String userId) {
        String type = permissionMapper.selectPermissionType(scriptId, userId);
        return ProjectConstants.ScriptPermissionType.ADMIN.equals(type);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 验证当前用户是 workspace ADMIN 或 CREATOR
     */
    private void requireWorkspaceAdmin() {
        String role = UserContextHolder.getWorkspaceRole();
        if (!isWorkspaceAdmin(role)) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
    }

    /**
     * 验证调用者有 script ADMIN 权限 OR workspace ADMIN+
     */
    private void requireScriptAdminOrWorkspaceAdmin(String scriptId, String operatorId) {
        String role = UserContextHolder.getWorkspaceRole();
        if (isWorkspaceAdmin(role)) {
            return;
        }
        if (!hasAdminPermission(scriptId, operatorId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
    }

    private boolean isWorkspaceAdmin(String role) {
        return "ADMIN".equals(role) || "CREATOR".equals(role);
    }

    private boolean isWorkspaceMember(String workspaceId, String userId) {
        try {
            Result<WorkspaceMembershipInfo> result = workspaceFeignClient.getMembership(workspaceId, userId);
            return result != null && result.isSuccess()
                    && result.getData() != null && result.getData().isMember();
        } catch (Exception e) {
            log.warn("查询工作空间成员身份失败: workspaceId={}, userId={}, error={}", workspaceId, userId, e.getMessage());
            return false;
        }
    }

    private void addGuestMember(String workspaceId, String userId, String invitedBy) {
        try {
            Result<Void> result = workspaceFeignClient.addGuestMember(workspaceId, userId, invitedBy);
            if (result == null || !result.isSuccess()) {
                String msg = result != null ? result.getMessage() : "工作空间服务无响应";
                log.error("自动添加 Guest 成员失败: workspaceId={}, userId={}, error={}", workspaceId, userId, msg);
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "添加工作空间成员失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用工作空间服务异常: workspaceId={}, userId={}, error={}", workspaceId, userId, e.getMessage());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "工作空间服务调用失败");
        }
    }

    private ScriptPermission buildPermission(String scriptId, String workspaceId, String userId,
                                              String permissionType, String grantSource,
                                              String grantedBy, LocalDateTime expiresAt) {
        ScriptPermission permission = new ScriptPermission();
        permission.setId(UuidGenerator.generateUuidV7());
        permission.setWorkspaceId(workspaceId);
        permission.setScriptId(scriptId);
        permission.setUserId(userId);
        permission.setPermissionType(permissionType);
        permission.setGrantSource(grantSource);
        permission.setGrantedBy(grantedBy);
        permission.setGrantedAt(LocalDateTime.now());
        permission.setExpiresAt(expiresAt);
        return permission;
    }

    private ScriptPermissionResponse enrichResponse(ScriptPermissionResponse response) {
        UserBasicInfo info = userInfoHelper.getUserInfo(response.getUserId());
        if (info != null) {
            response.setUsername(info.getUsername());
            response.setNickname(info.getNickname());
            response.setAvatar(info.getAvatar());
        }
        return response;
    }
}
