package com.actionow.workspace.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.workspace.constant.WorkspaceConstants;
import com.actionow.workspace.dto.CreateInvitationRequest;
import com.actionow.workspace.dto.InvitationResponse;
import com.actionow.workspace.dto.WorkspaceMemberResponse;
import com.actionow.workspace.entity.WorkspaceInvitation;
import com.actionow.workspace.entity.WorkspaceMember;
import com.actionow.workspace.enums.WorkspaceErrorCode;
import com.actionow.workspace.mapper.WorkspaceInvitationMapper;
import com.actionow.workspace.service.WorkspaceInvitationService;
import com.actionow.workspace.service.WorkspaceMemberService;
import com.actionow.workspace.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作空间邀请服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceInvitationServiceImpl implements WorkspaceInvitationService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

    private final WorkspaceInvitationMapper invitationMapper;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService memberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationResponse createInvitation(String workspaceId, CreateInvitationRequest request, String inviterId) {
        // 检查工作空间是否存在
        workspaceService.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        // 检查权限（只有Creator和Admin可以创建邀请）
        if (!workspaceService.hasMinimumRole(workspaceId, inviterId, WorkspaceConstants.Role.ADMIN)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        // 校验角色（不能邀请为Creator）
        if (WorkspaceConstants.Role.CREATOR.equals(request.getRole())) {
            throw new BusinessException(WorkspaceErrorCode.CANNOT_INVITE_AS_CREATOR);
        }
        if (!WorkspaceConstants.Role.isValid(request.getRole())) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_ROLE);
        }

        // 创建邀请
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.setId(UuidGenerator.generateUuidV7());
        invitation.setWorkspaceId(workspaceId);
        invitation.setCode(generateUniqueInviteCode());
        invitation.setInviterId(inviterId);
        invitation.setInviteeEmail(request.getEffectiveEmail());
        invitation.setRole(request.getRole());
        invitation.setExpiresAt(LocalDateTime.now().plusHours(request.getExpireHours()));
        invitation.setMaxUses(request.getMaxUses());
        invitation.setUsedCount(0);
        invitation.setStatus("ACTIVE");

        invitationMapper.insert(invitation);

        log.info("邀请创建成功: workspaceId={}, code={}, inviterId={}", workspaceId, invitation.getCode(), inviterId);

        return InvitationResponse.fromEntity(invitation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemberResponse acceptInvitation(String code, String userId, String email) {
        WorkspaceInvitation invitation = invitationMapper.selectByCode(code);
        if (invitation == null) {
            throw new BusinessException(WorkspaceErrorCode.INVITATION_INVALID);
        }

        // 检查是否过期
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(WorkspaceErrorCode.INVITATION_EXPIRED);
        }

        // 检查使用次数
        if (invitation.getUsedCount() >= invitation.getMaxUses()) {
            throw new BusinessException(WorkspaceErrorCode.INVITATION_USED_UP);
        }

        // 检查邮箱限制
        if (invitation.getInviteeEmail() != null && !invitation.getInviteeEmail().isEmpty()) {
            if (!invitation.getInviteeEmail().equalsIgnoreCase(email)) {
                throw new BusinessException(WorkspaceErrorCode.INVITATION_EMAIL_MISMATCH);
            }
        }

        // 检查是否已是成员
        if (memberService.isMember(invitation.getWorkspaceId(), userId)) {
            throw new BusinessException(WorkspaceErrorCode.ALREADY_MEMBER);
        }

        // 添加成员
        WorkspaceMember member = memberService.addMember(
                invitation.getWorkspaceId(),
                userId,
                invitation.getRole(),
                invitation.getInviterId()
        );

        // 增加使用次数
        invitationMapper.incrementUsedCount(invitation.getId());

        log.info("邀请接受成功: code={}, userId={}, workspaceId={}", code, userId, invitation.getWorkspaceId());

        return WorkspaceMemberResponse.fromEntity(member);
    }

    @Override
    public InvitationResponse getInvitationByCode(String code) {
        WorkspaceInvitation invitation = invitationMapper.selectByCode(code);
        if (invitation == null) {
            throw new BusinessException(WorkspaceErrorCode.INVITATION_INVALID);
        }
        return InvitationResponse.fromEntity(invitation);
    }

    @Override
    public List<InvitationResponse> listInvitations(String workspaceId, String operatorId) {
        // 检查权限
        if (!workspaceService.hasMinimumRole(workspaceId, operatorId, WorkspaceConstants.Role.ADMIN)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        List<WorkspaceInvitation> invitations = invitationMapper.selectByWorkspaceId(workspaceId);
        return invitations.stream()
                .map(InvitationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<InvitationResponse> listInvitationsPage(String workspaceId, String operatorId, Long current, Long size) {
        // 检查权限
        if (!workspaceService.hasMinimumRole(workspaceId, operatorId, WorkspaceConstants.Role.ADMIN)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

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
        Page<WorkspaceInvitation> page = new Page<>(current, size);
        IPage<WorkspaceInvitation> invitationPage = invitationMapper.selectPageByWorkspaceId(page, workspaceId);

        if (invitationPage.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        List<InvitationResponse> records = invitationPage.getRecords().stream()
                .map(InvitationResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(invitationPage.getCurrent(), invitationPage.getSize(), invitationPage.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableInvitation(String invitationId, String operatorId) {
        WorkspaceInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null) {
            throw new BusinessException(WorkspaceErrorCode.INVITATION_INVALID);
        }

        // 检查权限
        if (!workspaceService.hasMinimumRole(invitation.getWorkspaceId(), operatorId, WorkspaceConstants.Role.ADMIN)) {
            throw new BusinessException(WorkspaceErrorCode.NO_PERMISSION);
        }

        invitationMapper.disableInvitation(invitationId);

        log.info("邀请已禁用: invitationId={}, operatorId={}", invitationId, operatorId);
    }

    /**
     * 生成唯一邀请码（带碰撞重试）
     */
    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = generateInviteCode();
            if (invitationMapper.countByCode(code) == 0) {
                return code;
            }
            log.warn("邀请码碰撞，重试生成: attempt={}, code={}", attempt + 1, code);
        }
        // 极端情况：多次碰撞后使用带时间戳的码
        String fallbackCode = generateInviteCode() + System.currentTimeMillis() % 1000;
        log.warn("邀请码生成回退: code={}", fallbackCode);
        return fallbackCode.substring(0, Math.min(fallbackCode.length(), 12));
    }

    /**
     * 生成随机邀请码
     */
    private String generateInviteCode() {
        StringBuilder code = new StringBuilder(WorkspaceConstants.INVITATION_CODE_LENGTH);
        for (int i = 0; i < WorkspaceConstants.INVITATION_CODE_LENGTH; i++) {
            code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return code.toString();
    }
}
