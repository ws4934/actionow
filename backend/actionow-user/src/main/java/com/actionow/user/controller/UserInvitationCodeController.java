package com.actionow.user.controller;

import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.web.controller.BaseController;
import com.actionow.user.dto.response.*;
import com.actionow.user.entity.InvitationCode;
import com.actionow.user.service.InvitationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户邀请码控制器
 *
 * @author Actionow
 */
@Slf4j
@Tag(name = "用户邀请码", description = "用户专属邀请码管理接口")
@RestController
@RequestMapping("/user/invitation-code")
@RequiredArgsConstructor
public class UserInvitationCodeController extends BaseController {

    private final InvitationCodeService invitationCodeService;

    @Operation(summary = "获取我的邀请码")
    @GetMapping
    @RequireLogin
    public Result<UserInvitationCodeResponse> getMyCode() {
        String userId = getCurrentUserId();
        InvitationCode code = invitationCodeService.getUserActiveCode(userId);

        // 如果没有则自动生成
        if (code == null && invitationCodeService.isUserCodeEnabled()) {
            code = invitationCodeService.generateUserCode(userId);
        }

        if (code == null) {
            return success(null);
        }

        UserInvitationCodeResponse response = invitationCodeService.getUserCodeResponse(userId);
        return success(response);
    }

    @Operation(summary = "刷新邀请码")
    @PostMapping("/refresh")
    @RequireLogin
    public Result<UserInvitationCodeResponse> refreshCode() {
        String userId = getCurrentUserId();
        invitationCodeService.refreshUserCode(userId);
        UserInvitationCodeResponse response = invitationCodeService.getUserCodeResponse(userId);
        return success(response, "邀请码已刷新");
    }

    @Operation(summary = "获取我邀请的用户列表")
    @GetMapping("/invitees")
    @RequireLogin
    public Result<PageResult<InviteeResponse>> getInvitees(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        String userId = getCurrentUserId();
        PageResult<InviteeResponse> response = invitationCodeService.getInvitees(userId, page, size);
        return success(response);
    }

    @Operation(summary = "获取我的邀请人")
    @GetMapping("/inviter")
    @RequireLogin
    public Result<InviterResponse> getInviter() {
        String userId = getCurrentUserId();
        InviterResponse response = invitationCodeService.getInviter(userId);
        return success(response);
    }

    @Operation(summary = "验证邀请码")
    @GetMapping("/validate/{code}")
    @IgnoreAuth
    public Result<InvitationCodeValidateResponse> validateCode(@PathVariable String code) {
        InvitationCodeValidateResponse response = invitationCodeService.validateCode(code);
        return success(response);
    }

    @Operation(summary = "获取注册配置")
    @GetMapping("/registration-config")
    @IgnoreAuth
    public Result<RegistrationConfigResponse> getRegistrationConfig() {
        RegistrationConfigResponse response = invitationCodeService.getRegistrationConfig();
        return success(response);
    }
}
