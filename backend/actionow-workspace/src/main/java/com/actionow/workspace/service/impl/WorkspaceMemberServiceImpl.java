package com.actionow.workspace.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.workspace.constant.WorkspaceConstants;
import com.actionow.workspace.dto.WorkspaceMemberResponse;
import com.actionow.workspace.entity.Workspace;
import com.actionow.workspace.entity.WorkspaceMember;
import com.actionow.workspace.enums.WorkspaceErrorCode;
import com.actionow.workspace.feign.UserBasicInfo;
import com.actionow.workspace.feign.UserFeignClient;
import com.actionow.workspace.feign.WalletFeignClient;
import com.actionow.workspace.mapper.WorkspaceMemberMapper;
import com.actionow.workspace.mapper.WorkspaceMapper;
import com.actionow.workspace.service.WorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 工作空间成员服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceMemberServiceImpl implements WorkspaceMemberService {

    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int RANDOM_SUFFIX_LENGTH = 4;

    private final WorkspaceMemberMapper memberMapper;
    private final WorkspaceMapper workspaceMapper;
    private final UserFeignClient userFeignClient;
    private final WalletFeignClient walletFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMember addMember(String workspaceId, String userId, String role, String invitedBy) {
        // 检查是否已是成员
        if (isMember(workspaceId, userId)) {
            throw new BusinessException(WorkspaceErrorCode.ALREADY_MEMBER);
        }

        // 检查工作空间是否存在
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || CommonConstants.DELETED == workspace.getDeleted()) {
            throw new BusinessException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        }

        // 检查成员数量限制
        int currentCount = countMembers(workspaceId);
        if (currentCount >= workspace.getMaxMembers()) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_LIMIT_EXCEEDED);
        }

        // 校验角色
        if (!WorkspaceConstants.Role.isValid(role)) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_ROLE);
        }

        // 获取用户昵称
        String nickname = fetchUserNickname(userId);

        // 若存在已删除记录，则直接恢复，避免唯一约束冲突
        WorkspaceMember existingMember = memberMapper.selectAnyByWorkspaceAndUser(workspaceId, userId);
        if (existingMember != null && CommonConstants.DELETED == existingMember.getDeleted()) {
            LocalDateTime joinedAt = LocalDateTime.now();
            int restored = memberMapper.restoreDeletedMember(
                    existingMember.getId(),
                    role,
                    WorkspaceConstants.MemberStatus.ACTIVE,
                    nickname,
                    invitedBy,
                    joinedAt
            );
            if (restored != 1) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR);
            }

            workspaceMapper.incrementMemberCount(workspaceId);

            existingMember.setDeleted(CommonConstants.NOT_DELETED);
            existingMember.setRole(role);
            existingMember.setStatus(WorkspaceConstants.MemberStatus.ACTIVE);
            existingMember.setNickname(nickname);
            existingMember.setInvitedBy(invitedBy);
            existingMember.setJoinedAt(joinedAt);

            log.info("成员恢复成功: workspaceId={}, userId={}, role={}", workspaceId, userId, role);
            return existingMember;
        }

        // 创建成员记录
        WorkspaceMember member = new WorkspaceMember();
        member.setId(UuidGenerator.generateUuidV7());
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        member.setNickname(nickname);
        member.setStatus(WorkspaceConstants.MemberStatus.ACTIVE);
        member.setInvitedBy(invitedBy);
        member.setJoinedAt(LocalDateTime.now());

        memberMapper.insert(member);

        // 原子性更新工作空间成员计数
        workspaceMapper.incrementMemberCount(workspaceId);

        log.info("成员添加成功: workspaceId={}, userId={}, role={}", workspaceId, userId, role);

        return member;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(String workspaceId, String userId, String operatorId) {
        WorkspaceMember member = memberMapper.selectByWorkspaceAndUser(workspaceId, userId);
        removeMember(workspaceId, member, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMemberById(String workspaceId, String memberId, String operatorId) {
        WorkspaceMember member = memberMapper.selectByIdAndWorkspace(memberId, workspaceId);
        removeMember(workspaceId, member, operatorId);
    }

    private void removeMember(String workspaceId, WorkspaceMember member, String operatorId) {
        if (member == null) {
            throw new BusinessException(WorkspaceErrorCode.NOT_MEMBER);
        }

        // Creator不能被移除
        if (WorkspaceConstants.Role.CREATOR.equals(member.getRole())) {
            throw new BusinessException(WorkspaceErrorCode.CANNOT_REMOVE_CREATOR);
        }

        // 检查操作者权限
        WorkspaceMember operator = memberMapper.selectByWorkspaceAndUser(workspaceId, operatorId);
        if (operator == null || !WorkspaceConstants.Role.canManageMembers(operator.getRole())) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        // 不能移除比自己权限高或相同的成员（除非是Creator）
        if (!WorkspaceConstants.Role.CREATOR.equals(operator.getRole())) {
            if (WorkspaceConstants.Role.getPriority(member.getRole()) >=
                    WorkspaceConstants.Role.getPriority(operator.getRole())) {
                throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
            }
        }

        // 软删除成员
        int deleted = memberMapper.softDeleteById(member.getId());
        if (deleted != 1) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR);
        }

        // 原子性减少成员计数
        workspaceMapper.decrementMemberCount(workspaceId);

        // 清理成员配额记录，避免幽灵数据（成员再次加入时配额干净）
        cleanupMemberQuota(workspaceId, member.getUserId(), operatorId);

        log.info("成员移除成功: workspaceId={}, userId={}, memberId={}, operatorId={}",
                workspaceId, member.getUserId(), member.getId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveWorkspace(String workspaceId, String userId) {
        WorkspaceMember member = memberMapper.selectByWorkspaceAndUser(workspaceId, userId);
        if (member == null) {
            throw new BusinessException(WorkspaceErrorCode.NOT_MEMBER);
        }

        // Creator不能直接退出，需要先转让
        if (WorkspaceConstants.Role.CREATOR.equals(member.getRole())) {
            throw new BusinessException(WorkspaceErrorCode.CREATOR_CANNOT_LEAVE);
        }

        int deleted = memberMapper.softDeleteById(member.getId());
        if (deleted != 1) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR);
        }

        // 原子性减少成员计数
        workspaceMapper.decrementMemberCount(workspaceId);

        // 清理成员配额记录
        cleanupMemberQuota(workspaceId, userId, userId);

        log.info("成员退出工作空间: workspaceId={}, userId={}", workspaceId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(String workspaceId, String userId, String newRole, String operatorId) {
        WorkspaceMember member = memberMapper.selectByWorkspaceAndUser(workspaceId, userId);
        updateMemberRole(member, newRole, workspaceId, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRoleById(String workspaceId, String memberId, String newRole, String operatorId) {
        WorkspaceMember member = memberMapper.selectByIdAndWorkspace(memberId, workspaceId);
        updateMemberRole(member, newRole, workspaceId, operatorId);
    }

    private void updateMemberRole(WorkspaceMember member, String newRole, String workspaceId, String operatorId) {
        if (member == null) {
            throw new BusinessException(WorkspaceErrorCode.NOT_MEMBER);
        }

        if (!WorkspaceConstants.Role.isValid(newRole)) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_ROLE);
        }

        // 检查操作者权限（这里允许系统内部调用，如转让所有权时）
        if (operatorId != null && !operatorId.equals(member.getUserId())) {
            WorkspaceMember operator = memberMapper.selectByWorkspaceAndUser(workspaceId, operatorId);
            if (operator == null) {
                throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
            }

            // 只有Creator可以设置Admin和Creator角色
            if ((WorkspaceConstants.Role.ADMIN.equals(newRole) || WorkspaceConstants.Role.CREATOR.equals(newRole))
                    && !WorkspaceConstants.Role.CREATOR.equals(operator.getRole())) {
                throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
            }
        }

        member.setRole(newRole);
        memberMapper.updateById(member);

        log.info("成员角色更新: workspaceId={}, userId={}, memberId={}, newRole={}",
                workspaceId, member.getUserId(), member.getId(), newRole);
    }

    @Override
    public List<WorkspaceMemberResponse> listMembers(String workspaceId) {
        List<WorkspaceMember> members = memberMapper.selectByWorkspaceId(workspaceId);

        if (members.isEmpty()) {
            return List.of();
        }

        // 批量获取用户信息
        List<String> userIds = members.stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toList());

        Map<String, UserBasicInfo> userInfoMap = batchFetchUserInfo(userIds);

        // 转换响应并填充用户信息
        return members.stream()
                .map(member -> {
                    WorkspaceMemberResponse response = WorkspaceMemberResponse.fromEntity(member);
                    UserBasicInfo userInfo = userInfoMap.get(member.getUserId());
                    if (userInfo != null) {
                        response.setUsername(userInfo.getUsername());
                        response.setAvatar(userInfo.getAvatar());
                    }
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<WorkspaceMemberResponse> listMembersPage(String workspaceId, Long current, Long size, String role) {
        // 参数校验
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        // 分页查询
        Page<WorkspaceMember> page = new Page<>(current, size);
        IPage<WorkspaceMember> memberPage = memberMapper.selectPageByWorkspaceId(page, workspaceId, role);

        if (memberPage.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        // 批量获取用户信息
        List<String> userIds = memberPage.getRecords().stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toList());

        Map<String, UserBasicInfo> userInfoMap = batchFetchUserInfo(userIds);

        // 转换响应并填充用户信息
        List<WorkspaceMemberResponse> records = memberPage.getRecords().stream()
                .map(member -> {
                    WorkspaceMemberResponse response = WorkspaceMemberResponse.fromEntity(member);
                    UserBasicInfo userInfo = userInfoMap.get(member.getUserId());
                    if (userInfo != null) {
                        response.setUsername(userInfo.getUsername());
                        response.setAvatar(userInfo.getAvatar());
                    }
                    return response;
                })
                .collect(Collectors.toList());

        return PageResult.of(memberPage.getCurrent(), memberPage.getSize(), memberPage.getTotal(), records);
    }

    @Override
    public Optional<WorkspaceMember> getMember(String workspaceId, String userId) {
        WorkspaceMember member = memberMapper.selectByWorkspaceAndUser(workspaceId, userId);
        return Optional.ofNullable(member);
    }

    @Override
    public boolean isMember(String workspaceId, String userId) {
        return getMember(workspaceId, userId).isPresent();
    }

    @Override
    public int countMembers(String workspaceId) {
        return memberMapper.countByWorkspaceId(workspaceId);
    }

    @Override
    public List<WorkspaceMember> getMembershipsByUser(String userId) {
        return memberMapper.selectByUserId(userId);
    }

    /**
     * 获取用户昵称，如果不存在则生成默认昵称
     */
    private String fetchUserNickname(String userId) {
        try {
            Result<UserBasicInfo> result = userFeignClient.getUserBasicInfo(userId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                String nickname = result.getData().getNickname();
                if (nickname != null && !nickname.isBlank()) {
                    return nickname;
                }
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: userId={}, error={}", userId, e.getMessage());
        }
        // 生成默认昵称: 用户-XXXX
        return generateDefaultNickname();
    }

    /**
     * 批量获取用户基本信息
     */
    private Map<String, UserBasicInfo> batchFetchUserInfo(List<String> userIds) {
        try {
            Result<Map<String, UserBasicInfo>> result = userFeignClient.batchGetUserBasicInfo(userIds);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: userIds={}, error={}", userIds, e.getMessage());
        }
        return Map.of();
    }

    /**
     * 生成默认昵称: 用户-XXXX
     */
    private String generateDefaultNickname() {
        StringBuilder sb = new StringBuilder("用户-");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < RANDOM_SUFFIX_LENGTH; i++) {
            sb.append(RANDOM_CHARS.charAt(random.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 清理成员配额记录（跨服务调用，失败不影响主流程）
     * 成员被移除或主动退出时调用，防止幽灵 quota 数据
     */
    private void cleanupMemberQuota(String workspaceId, String userId, String operatorId) {
        try {
            Result<Void> result = walletFeignClient.deleteQuota(workspaceId, userId, operatorId);
            if (result == null || !result.isSuccess()) {
                log.warn("清理成员配额失败（不影响主流程）: workspaceId={}, userId={}", workspaceId, userId);
            }
        } catch (Exception e) {
            log.warn("调用钱包服务清理配额异常（不影响主流程）: workspaceId={}, userId={}, error={}",
                    workspaceId, userId, e.getMessage());
        }
    }
}
