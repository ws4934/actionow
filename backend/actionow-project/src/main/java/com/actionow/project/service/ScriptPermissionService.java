package com.actionow.project.service;

import com.actionow.project.dto.GrantScriptPermissionRequest;
import com.actionow.project.dto.InviteScriptCollaboratorRequest;
import com.actionow.project.dto.ScriptPermissionResponse;

import java.util.List;

/**
 * 剧本权限服务接口
 * 实现剧本维度的细粒度权限控制
 *
 * @author Actionow
 */
public interface ScriptPermissionService {

    /**
     * 管理员授权用户访问剧本
     *
     * @param scriptId   剧本ID
     * @param request    授权请求
     * @param workspaceId 工作空间ID
     * @param operatorId  操作者ID
     * @return 权限记录响应
     */
    ScriptPermissionResponse grantPermission(String scriptId, GrantScriptPermissionRequest request,
                                              String workspaceId, String operatorId);

    /**
     * 撤销用户对剧本的访问权限
     *
     * @param scriptId   剧本ID
     * @param userId     被撤权用户ID
     * @param operatorId  操作者ID
     */
    void revokePermission(String scriptId, String userId, String operatorId);

    /**
     * 查询剧本的所有权限列表
     *
     * @param scriptId 剧本ID
     * @return 权限列表（含用户信息富化）
     */
    List<ScriptPermissionResponse> listPermissions(String scriptId);

    /**
     * 剧本创建者邀请协作者
     * 若被邀请者尚未加入工作空间，自动以 GUEST 角色添加
     *
     * @param scriptId   剧本ID
     * @param request    邀请请求
     * @param workspaceId 工作空间ID
     * @param operatorId  操作者ID（需具有 script ADMIN 权限或 workspace ADMIN+）
     * @return 权限记录响应
     */
    ScriptPermissionResponse inviteCollaborator(String scriptId, InviteScriptCollaboratorRequest request,
                                                String workspaceId, String operatorId);

    /**
     * 移除协作者（剧本创建者操作）
     *
     * @param scriptId   剧本ID
     * @param userId     被移除的用户ID
     * @param operatorId  操作者ID（需具有 script ADMIN 权限或 workspace ADMIN+）
     */
    void removeCollaborator(String scriptId, String userId, String operatorId);

    /**
     * 为剧本创建者自动授予 ADMIN 权限（创建剧本时调用）
     *
     * @param scriptId    剧本ID
     * @param workspaceId 工作空间ID
     * @param userId      创建者ID
     */
    void createOwnerPermission(String scriptId, String workspaceId, String userId);

    /**
     * 检查用户是否具有查看权限（VIEW ∨ EDIT ∨ ADMIN）
     *
     * @param scriptId 剧本ID
     * @param userId   用户ID
     * @return 是否有查看权限
     */
    boolean hasViewPermission(String scriptId, String userId);

    /**
     * 检查用户是否具有编辑权限（EDIT ∨ ADMIN）
     *
     * @param scriptId 剧本ID
     * @param userId   用户ID
     * @return 是否有编辑权限
     */
    boolean hasEditPermission(String scriptId, String userId);

    /**
     * 检查用户是否具有 ADMIN 权限
     *
     * @param scriptId 剧本ID
     * @param userId   用户ID
     * @return 是否有 ADMIN 权限
     */
    boolean hasAdminPermission(String scriptId, String userId);
}
