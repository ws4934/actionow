package com.actionow.workspace.service.impl;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.data.tenant.TenantSchemaService;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.outbox.TransactionalMessageProducer;
import com.actionow.workspace.constant.WorkspaceConstants;
import com.actionow.workspace.enums.WorkspaceErrorCode;
import com.actionow.workspace.dto.*;
import com.actionow.workspace.entity.Workspace;
import com.actionow.workspace.entity.WorkspaceMember;
import com.actionow.workspace.feign.UserBasicInfo;
import com.actionow.workspace.feign.UserFeignClient;
import com.actionow.workspace.mapper.WorkspaceInvitationMapper;
import com.actionow.workspace.mapper.WorkspaceMemberMapper;
import com.actionow.workspace.mapper.WorkspaceMapper;
import com.actionow.workspace.service.WorkspaceMemberService;
import com.actionow.workspace.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作空间服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final WorkspaceInvitationMapper invitationMapper;
    private final WorkspaceMemberService workspaceMemberService;
    private final TenantSchemaService tenantSchemaService;
    private final UserFeignClient userFeignClient;
    private final TransactionalMessageProducer transactionalMessageProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceResponse create(CreateWorkspaceRequest request, String userId) {
        // 检查用户创建工作空间数量限制
        int ownedCount = workspaceMapper.countByOwnerId(userId);
        if (ownedCount >= WorkspaceConstants.MAX_WORKSPACES_PER_USER) {
            throw new BusinessException(WorkspaceErrorCode.WORKSPACE_LIMIT_EXCEEDED);
        }

        // 检查同名工作空间
        int count = workspaceMapper.countByNameAndOwner(request.getName(), userId);
        if (count > 0) {
            throw new BusinessException(WorkspaceErrorCode.WORKSPACE_NAME_EXISTS);
        }

        // 生成工作空间ID
        String workspaceId = UuidGenerator.generateUuidV7();

        // 生成slug（如果未提供则根据名称生成）
        String slug = request.getSlug();
        if (slug == null || slug.isEmpty()) {
            slug = generateSlug(request.getName(), workspaceId);
        } else {
            // 检查slug是否已存在
            if (workspaceMapper.countBySlug(slug) > 0) {
                throw new BusinessException(WorkspaceErrorCode.SLUG_EXISTS);
            }
        }

        // 生成Schema名称: tenant_{id前8位}_{时间戳}
        String schemaName = String.format("tenant_%s_%d",
                workspaceId.replace("-", "").substring(0, 8),
                System.currentTimeMillis());

        // 创建工作空间
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName(request.getName());
        workspace.setSlug(slug);
        workspace.setDescription(request.getDescription());
        workspace.setLogoUrl(request.getLogoUrl());
        workspace.setOwnerId(userId);
        workspace.setSchemaName(schemaName);
        workspace.setStatus(WorkspaceConstants.Status.ACTIVE);
        workspace.setPlanType(WorkspaceConstants.PlanType.FREE);
        workspace.setMaxMembers(WorkspaceConstants.DEFAULT_MEMBER_LIMIT);
        workspace.setMemberCount(0);
        workspace.setConfig(new HashMap<>());
        workspace.setDeleted(CommonConstants.NOT_DELETED);

        workspaceMapper.insert(workspace);

        // 初始化租户 Schema
        try {
            tenantSchemaService.initializeSchema(workspace.getSchemaName());
            log.info("租户Schema初始化成功: {}", workspace.getSchemaName());
        } catch (Exception e) {
            log.error("租户Schema初始化失败: {}, error: {}", workspace.getSchemaName(), e.getMessage());
            // 回滚工作空间创建
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "工作空间创建失败：Schema初始化错误");
        }

        // 添加创建者为Creator成员
        workspaceMemberService.addMember(workspace.getId(), userId, WorkspaceConstants.Role.CREATOR, null);

        // 通过 Outbox 保证钱包创建消息与业务事务原子性提交
        // 事务回滚时 outbox 记录也回滚，事务提交后由 OutboxMessagePublisher 异步投递
        transactionalMessageProducer.sendDirectInTransaction(
                MqConstants.Wallet.ROUTING_CREATE_COMPENSATION,
                MqConstants.Wallet.MSG_CREATE_COMPENSATION,
                workspace.getId()
        );

        log.info("工作空间创建成功: workspaceId={}, name={}, slug={}, ownerId={}",
                workspace.getId(), workspace.getName(), workspace.getSlug(), userId);

        WorkspaceResponse response = WorkspaceResponse.fromEntity(workspace);
        response.setMyRole(WorkspaceConstants.Role.CREATOR);
        return response;
    }

    /**
     * 根据名称生成slug
     */
    private String generateSlug(String name, String workspaceId) {
        // 简单的slug生成：将中文转为拼音或使用ID前缀
        // 这里暂用workspaceId前8位作为后缀保证唯一性
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (baseSlug.isEmpty() || baseSlug.matches(".*[\\u4e00-\\u9fa5].*")) {
            // 如果包含中文或为空，使用workspace前缀
            baseSlug = "workspace";
        }

        return baseSlug + "-" + workspaceId.replace("-", "").substring(0, 8);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceResponse update(String workspaceId, UpdateWorkspaceRequest request, String userId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);

        // 检查权限（ADMIN 及以上可以编辑）
        if (!hasMinimumRole(workspaceId, userId, WorkspaceConstants.Role.ADMIN)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        // 检查同名
        if (request.getName() != null && !request.getName().equals(workspace.getName())) {
            int count = workspaceMapper.countByNameAndOwner(request.getName(), workspace.getOwnerId());
            if (count > 0) {
                throw new BusinessException(WorkspaceErrorCode.WORKSPACE_NAME_EXISTS);
            }
            workspace.setName(request.getName());
        }

        if (request.getDescription() != null) {
            workspace.setDescription(request.getDescription());
        }
        if (request.getLogoUrl() != null) {
            workspace.setLogoUrl(request.getLogoUrl());
        }
        if (request.getConfig() != null) {
            workspace.setConfig(request.getConfig());
        }

        workspaceMapper.updateById(workspace);

        log.info("工作空间更新成功: workspaceId={}, operatorId={}", workspaceId, userId);

        WorkspaceResponse response = WorkspaceResponse.fromEntity(workspace);
        setUserRole(response, workspaceId, userId);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String userId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);

        // 只有Creator可以删除
        if (!workspace.getOwnerId().equals(userId)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        // 使用软删除
        workspace.setDeleted(CommonConstants.DELETED);
        workspace.setDeletedAt(java.time.LocalDateTime.now());
        workspace.setStatus(WorkspaceConstants.Status.DELETED);
        workspaceMapper.updateById(workspace);

        // 级联：批量软删除所有成员、禁用所有活跃邀请
        int deletedMembers = memberMapper.softDeleteByWorkspaceId(workspaceId);
        int disabledInvitations = invitationMapper.disableByWorkspaceId(workspaceId);

        // 通过 Outbox 通知钱包服务关闭钱包（解冻冻结金额 + 标记 CLOSED）
        transactionalMessageProducer.sendDirectInTransaction(
                MqConstants.Wallet.ROUTING_CLOSE,
                MqConstants.Wallet.MSG_CLOSE,
                Map.of("workspaceId", workspaceId, "operatorId", userId)
        );

        log.info("工作空间删除成功: workspaceId={}, operatorId={}, deletedMembers={}, disabledInvitations={}",
                workspaceId, userId, deletedMembers, disabledInvitations);
    }

    @Override
    public WorkspaceResponse getById(String workspaceId, String userId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);

        // 检查是否是成员
        if (!workspaceMemberService.isMember(workspaceId, userId)) {
            throw new BusinessException(WorkspaceErrorCode.NOT_MEMBER);
        }

        WorkspaceResponse response = WorkspaceResponse.fromEntity(workspace);
        setUserRole(response, workspaceId, userId);
        setOwnerInfo(response, workspace.getOwnerId());
        return response;
    }

    @Override
    public Optional<Workspace> findById(String workspaceId) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || CommonConstants.DELETED == workspace.getDeleted()) {
            return Optional.empty();
        }
        return Optional.of(workspace);
    }

    @Override
    public List<WorkspaceResponse> listByUser(String userId) {
        // 获取用户加入的所有工作空间成员记录
        List<WorkspaceMember> memberships = workspaceMemberService.getMembershipsByUser(userId);

        if (memberships.isEmpty()) {
            return List.of();
        }

        // 批量查询所有工作空间（避免 N+1）
        List<String> workspaceIds = memberships.stream()
                .map(WorkspaceMember::getWorkspaceId)
                .collect(Collectors.toList());

        List<Workspace> workspaces = workspaceMapper.selectByIds(workspaceIds);

        // 构建 workspaceId -> Workspace 映射
        Map<String, Workspace> workspaceMap = workspaces.stream()
                .collect(Collectors.toMap(Workspace::getId, ws -> ws));

        // 构建 workspaceId -> role 映射
        Map<String, String> roleMap = memberships.stream()
                .collect(Collectors.toMap(WorkspaceMember::getWorkspaceId, WorkspaceMember::getRole));

        // 组装结果
        List<WorkspaceResponse> result = new ArrayList<>();
        for (String workspaceId : workspaceIds) {
            Workspace workspace = workspaceMap.get(workspaceId);
            if (workspace != null) {
                WorkspaceResponse response = WorkspaceResponse.fromEntity(workspace);
                response.setMyRole(roleMap.get(workspaceId));
                result.add(response);
            }
        }

        return result;
    }

    @Override
    public List<WorkspaceResponse> listOwnedByUser(String userId) {
        List<Workspace> workspaces = workspaceMapper.selectByOwnerId(userId);
        return workspaces.stream()
                .map(ws -> {
                    WorkspaceResponse response = WorkspaceResponse.fromEntity(ws);
                    response.setMyRole(WorkspaceConstants.Role.CREATOR);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferOwnership(String workspaceId, String newOwnerId, String currentOwner) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);

        // 只有当前Creator可以转让
        if (!workspace.getOwnerId().equals(currentOwner)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        // 检查新所有者是否是成员
        if (!workspaceMemberService.isMember(workspaceId, newOwnerId)) {
            throw new BusinessException(WorkspaceErrorCode.NOT_MEMBER);
        }

        // 更新工作空间所有者
        workspace.setOwnerId(newOwnerId);
        workspaceMapper.updateById(workspace);

        // 更新成员角色
        workspaceMemberService.updateMemberRole(workspaceId, newOwnerId, WorkspaceConstants.Role.CREATOR, currentOwner);
        workspaceMemberService.updateMemberRole(workspaceId, currentOwner, WorkspaceConstants.Role.ADMIN, currentOwner);

        log.info("工作空间所有权转让成功: workspaceId={}, from={}, to={}", workspaceId, currentOwner, newOwnerId);
    }

    @Override
    public boolean hasMinimumRole(String workspaceId, String userId, String minRole) {
        return workspaceMemberService.getMember(workspaceId, userId)
                .map(member -> WorkspaceConstants.Role.getPriority(member.getRole())
                        >= WorkspaceConstants.Role.getPriority(minRole))
                .orElse(false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(String workspaceId, String planType, String userId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);

        // 只有 Creator 可以变更计划
        if (!workspace.getOwnerId().equals(userId)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        applyPlanChange(workspace, planType, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlanInternal(String workspaceId, String planType, String operatorId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        applyPlanChange(workspace, planType, operatorId, true);
    }

    private void applyPlanChange(Workspace workspace, String planType, String operatorId, boolean internalCall) {
        if (!WorkspaceConstants.PlanType.isValid(planType)) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_PLAN_TYPE);
        }

        String oldPlan = workspace.getPlanType();
        if (planType.equals(oldPlan)) {
            log.info("工作空间计划未变化，跳过更新: workspaceId={}, planType={}, operatorId={}, internalCall={}",
                    workspace.getId(), planType, operatorId, internalCall);
            return;
        }

        workspace.setPlanType(planType);
        workspace.setMaxMembers(WorkspaceConstants.PlanType.getMaxMembers(planType));
        workspaceMapper.updateById(workspace);

        log.info("工作空间计划变更: workspaceId={}, from={}, to={}, operatorId={}, internalCall={}",
                workspace.getId(), oldPlan, planType, operatorId, internalCall);

        // 通过 Outbox 通知钱包服务调整所有成员的配额上限
        transactionalMessageProducer.sendDirectInTransaction(
                MqConstants.Wallet.ROUTING_ADJUST_PLAN,
                MqConstants.Wallet.MSG_ADJUST_PLAN,
                Map.of("workspaceId", workspace.getId(), "planType", planType)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateScriptCreationSetting(String workspaceId, boolean enabled, String operatorId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);

        Map<String, Object> config = workspace.getConfig();
        if (config == null) {
            config = new HashMap<>();
        }

        // 深度更新 config.permissions.memberCanCreateScript
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) config.computeIfAbsent("permissions", k -> new HashMap<>());
        permissions.put("memberCanCreateScript", enabled);

        workspace.setConfig(config);
        workspaceMapper.updateById(workspace);

        log.info("剧本创建权限开关更新: workspaceId={}, memberCanCreateScript={}, operatorId={}",
                workspaceId, enabled, operatorId);
    }

    private Workspace getWorkspaceOrThrow(String workspaceId) {
        return findById(workspaceId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
    }

    private void setUserRole(WorkspaceResponse response, String workspaceId, String userId) {
        workspaceMemberService.getMember(workspaceId, userId)
                .ifPresent(member -> response.setMyRole(member.getRole()));
    }

    /**
     * 设置所有者信息
     */
    private void setOwnerInfo(WorkspaceResponse response, String ownerId) {
        try {
            Result<UserBasicInfo> result = userFeignClient.getUserBasicInfo(ownerId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                UserBasicInfo userInfo = result.getData();
                response.setOwner(WorkspaceResponse.OwnerInfo.builder()
                        .id(userInfo.getId())
                        .username(userInfo.getUsername())
                        .nickname(userInfo.getNickname())
                        .avatar(userInfo.getAvatar())
                        .build());
            }
        } catch (Exception e) {
            log.warn("获取所有者信息失败: ownerId={}, error={}", ownerId, e.getMessage());
            // 设置基本信息
            response.setOwner(WorkspaceResponse.OwnerInfo.builder()
                    .id(ownerId)
                    .build());
        }
    }

}
