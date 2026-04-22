package com.actionow.agent.billing.controller;

import com.actionow.agent.billing.dto.BillingSessionResponse;
import com.actionow.agent.billing.service.AgentBillingService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireSystemTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 计费 Controller
 * 提供计费会话查询接口
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/agent/billing")
@RequiredArgsConstructor
@Tag(name = "Agent 计费", description = "Agent 计费会话管理")
public class AgentBillingController {

    private final AgentBillingService billingService;

    /**
     * 分页获取所有计费会话
     */
    @GetMapping
    @RequireSystemTenant(minRole = "ADMIN")
    @Operation(summary = "分页获取计费会话", description = "分页获取所有计费会话列表")
    public Result<PageResult<BillingSessionResponse>> getAllBillingSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<BillingSessionResponse> result = billingService.getAllBillingSessions(page, size);
        return Result.success(result);
    }

    /**
     * 获取计费会话详情
     */
    @GetMapping("/sessions/{conversationId}")
    @RequireWorkspaceMember
    @Operation(summary = "获取计费会话详情", description = "根据会话 ID 查询计费会话信息")
    public Result<BillingSessionResponse> getBillingSession(
            @PathVariable String conversationId) {
        BillingSessionResponse session = billingService.getBillingSession(conversationId);
        if (session == null) {
            return Result.fail("计费会话不存在");
        }
        return Result.success(session);
    }

    /**
     * 获取用户的计费会话历史（userId 固定取自安全上下文，避免越权查询）
     */
    @GetMapping("/sessions")
    @RequireWorkspaceMember
    @Operation(summary = "获取计费会话历史", description = "查询当前用户的计费会话列表")
    public Result<List<BillingSessionResponse>> getUserBillingSessions(
            @RequestParam(defaultValue = "20") int limit) {
        String userId = UserContextHolder.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("用户身份信息缺失");
        }
        List<BillingSessionResponse> sessions = billingService.getUserBillingSessions(userId, limit);
        return Result.success(sessions);
    }

    /**
     * 手动结算计费会话（管理员接口）。workspaceId/userId 固定取自安全上下文。
     */
    @PostMapping("/sessions/{conversationId}/settle")
    @RequireSystemTenant(minRole = "ADMIN")
    @Operation(summary = "手动结算计费会话", description = "管理员手动结算指定的计费会话")
    public Result<BillingSessionResponse> settleBillingSession(
            @PathVariable String conversationId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        BillingSessionResponse session = billingService.settleBillingSession(
                conversationId, workspaceId, userId);
        return Result.success(session);
    }

    /**
     * 取消计费会话（管理员接口）。workspaceId/userId 固定取自安全上下文。
     */
    @PostMapping("/sessions/{conversationId}/cancel")
    @RequireSystemTenant(minRole = "ADMIN")
    @Operation(summary = "取消计费会话", description = "管理员取消指定的计费会话并解冻积分")
    public Result<Void> cancelBillingSession(
            @PathVariable String conversationId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        billingService.cancelBillingSession(conversationId, workspaceId, userId);
        return Result.success();
    }
}
