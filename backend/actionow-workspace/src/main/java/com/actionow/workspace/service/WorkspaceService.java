package com.actionow.workspace.service;

import com.actionow.workspace.dto.*;
import com.actionow.workspace.entity.Workspace;

import java.util.List;
import java.util.Optional;

/**
 * 工作空间服务接口
 *
 * @author Actionow
 */
public interface WorkspaceService {

    /**
     * 创建工作空间
     *
     * @param request 创建请求
     * @param userId  创建者ID
     * @return 工作空间响应
     */
    WorkspaceResponse create(CreateWorkspaceRequest request, String userId);

    /**
     * 更新工作空间
     *
     * @param workspaceId 工作空间ID
     * @param request     更新请求
     * @param userId      操作者ID
     * @return 更新后的工作空间
     */
    WorkspaceResponse update(String workspaceId, UpdateWorkspaceRequest request, String userId);

    /**
     * 删除工作空间（软删除）
     *
     * @param workspaceId 工作空间ID
     * @param userId      操作者ID
     */
    void delete(String workspaceId, String userId);

    /**
     * 获取工作空间详情
     *
     * @param workspaceId 工作空间ID
     * @param userId      当前用户ID
     * @return 工作空间响应
     */
    WorkspaceResponse getById(String workspaceId, String userId);

    /**
     * 根据ID获取工作空间实体
     *
     * @param workspaceId 工作空间ID
     * @return 工作空间实体
     */
    Optional<Workspace> findById(String workspaceId);

    /**
     * 获取用户的工作空间列表（包括拥有的和加入的）
     *
     * @param userId 用户ID
     * @return 工作空间列表
     */
    List<WorkspaceResponse> listByUser(String userId);

    /**
     * 获取用户拥有的工作空间列表
     *
     * @param userId 用户ID
     * @return 工作空间列表
     */
    List<WorkspaceResponse> listOwnedByUser(String userId);

    /**
     * 转让工作空间
     *
     * @param workspaceId  工作空间ID
     * @param newOwnerId   新所有者ID
     * @param currentOwner 当前所有者ID
     */
    void transferOwnership(String workspaceId, String newOwnerId, String currentOwner);

    /**
     * 检查用户是否具有不低于指定角色的权限（层级比较）
     * <p>
     * 角色层级: CREATOR(100) > ADMIN(80) > MEMBER(60) > GUEST(40)
     * 例如 hasMinimumRole(ws, user, "ADMIN") 对 CREATOR 和 ADMIN 都返回 true
     *
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @param minRole     最低要求角色
     * @return 是否满足最低角色要求
     */
    boolean hasMinimumRole(String workspaceId, String userId, String minRole);

    /**
     * 变更工作空间订阅计划
     * 变更后联动调整所有成员的配额上限
     *
     * @param workspaceId 工作空间ID
     * @param planType    新的计划类型（Free/Basic/Pro/Enterprise）
     * @param userId      操作者ID
     */
    void updatePlan(String workspaceId, String planType, String userId);

    /**
     * 内部服务同步工作空间订阅计划（跳过角色校验）
     * 由 Billing 服务在支付回调后调用
     *
     * @param workspaceId 工作空间ID
     * @param planType    新的计划类型（Free/Basic/Pro/Enterprise）
     * @param operatorId  操作者标识（服务账号）
     */
    void updatePlanInternal(String workspaceId, String planType, String operatorId);

    /**
     * 更新工作空间的剧本创建权限开关
     * 控制 MEMBER 角色是否可以创建剧本（CREATOR/ADMIN 不受此开关影响）
     *
     * @param workspaceId 工作空间ID
     * @param enabled     是否允许普通成员创建剧本
     * @param operatorId  操作者ID
     */
    void updateScriptCreationSetting(String workspaceId, boolean enabled, String operatorId);
}
