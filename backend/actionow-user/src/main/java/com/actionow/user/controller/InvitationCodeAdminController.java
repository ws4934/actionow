package com.actionow.user.controller;

import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.common.web.controller.BaseController;
import com.actionow.user.dto.request.BatchCreateInvitationCodeRequest;
import com.actionow.user.dto.request.CreateInvitationCodeRequest;
import com.actionow.user.dto.request.UpdateInvitationCodeRequest;
import com.actionow.user.dto.response.BatchCreateInvitationCodeResult;
import com.actionow.user.dto.response.InvitationCodeResponse;
import com.actionow.user.dto.response.InvitationCodeStatisticsResponse;
import com.actionow.user.dto.response.InvitationCodeUsageResponse;
import com.actionow.user.service.InvitationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 邀请码管理控制器（管理员）
 *
 * @author Actionow
 */
@Slf4j
@Tag(name = "邀请码管理", description = "管理员邀请码管理接口")
@RestController
@RequestMapping("/admin/invitation-codes")
@RequiredArgsConstructor
@RequireSystemTenant
public class InvitationCodeAdminController extends BaseController {

    private final InvitationCodeService invitationCodeService;

    @Operation(summary = "创建邀请码")
    @PostMapping
    public Result<InvitationCodeResponse> create(@Valid @RequestBody CreateInvitationCodeRequest request) {
        InvitationCodeResponse response = invitationCodeService.create(request, getCurrentUserId());
        return success(response, "邀请码创建成功");
    }

    @Operation(summary = "批量创建邀请码")
    @PostMapping("/batch")
    public Result<BatchCreateInvitationCodeResult> batchCreate(@Valid @RequestBody BatchCreateInvitationCodeRequest request) {
        BatchCreateInvitationCodeResult response = invitationCodeService.batchCreate(request, getCurrentUserId());
        return success(response, "批量创建完成");
    }

    @Operation(summary = "获取邀请码详情")
    @GetMapping("/{id}")
    public Result<InvitationCodeResponse> getById(@PathVariable String id) {
        InvitationCodeResponse response = invitationCodeService.getById(id);
        return success(response);
    }

    @Operation(summary = "分页查询邀请码列表")
    @GetMapping
    public Result<PageResult<InvitationCodeResponse>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "类型：System/User") @RequestParam(required = false) String type,
            @Parameter(description = "状态：Active/Disabled/Exhausted/Expired") @RequestParam(required = false) String status,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword) {
        PageResult<InvitationCodeResponse> response = invitationCodeService.listPage(page, size, type, status, keyword);
        return success(response);
    }

    @Operation(summary = "更新邀请码")
    @PatchMapping("/{id}")
    public Result<InvitationCodeResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateInvitationCodeRequest request) {
        InvitationCodeResponse response = invitationCodeService.update(id, request, getCurrentUserId());
        return success(response, "更新成功");
    }

    @Operation(summary = "更新邀请码状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable String id,
            @Parameter(description = "状态：Active/Disabled") @RequestParam String status) {
        invitationCodeService.updateStatus(id, status, getCurrentUserId());
        return success(null, "状态更新成功");
    }

    @Operation(summary = "删除邀请码")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        invitationCodeService.delete(id, getCurrentUserId());
        return success(null, "删除成功");
    }

    @Operation(summary = "获取邀请码使用记录")
    @GetMapping("/{id}/usages")
    public Result<PageResult<InvitationCodeUsageResponse>> getUsages(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<InvitationCodeUsageResponse> response = invitationCodeService.getUsages(id, page, size);
        return success(response);
    }

    @Operation(summary = "获取统计信息")
    @GetMapping("/statistics")
    public Result<InvitationCodeStatisticsResponse> getStatistics() {
        InvitationCodeStatisticsResponse response = invitationCodeService.getStatistics();
        return success(response);
    }
}
