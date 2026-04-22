package com.actionow.user.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.security.jwt.TokenResponse;
import com.actionow.common.web.controller.BaseController;
import com.actionow.user.dto.request.*;
import com.actionow.user.dto.response.LoginResponse;
import com.actionow.user.dto.response.SendVerifyCodeResponse;
import com.actionow.user.dto.response.UserResponse;
import com.actionow.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 *
 * @author Actionow
 */
@Slf4j
@Tag(name = "认证管理", description = "用户注册、登录、登出等认证相关接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthService authService;

    @Operation(summary = "发送验证码")
    @PostMapping("/verify-code")
    @IgnoreAuth
    public Result<SendVerifyCodeResponse> sendVerifyCode(@Valid @RequestBody SendVerifyCodeRequest request) {
        SendVerifyCodeResponse response = authService.sendVerifyCode(request);
        return success(response, "验证码已发送");
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    @IgnoreAuth
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return success(response, "注册成功");
    }

    @Operation(summary = "密码登录")
    @PostMapping("/login")
    @IgnoreAuth
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String clientIp = UserContextHolder.getContext().getClientIp();
        LoginResponse response = authService.login(request, clientIp);
        return success(response, "登录成功");
    }

    @Operation(summary = "验证码登录")
    @PostMapping("/login/code")
    @IgnoreAuth
    public Result<LoginResponse> loginByVerifyCode(@Valid @RequestBody VerifyCodeLoginRequest request) {
        String clientIp = UserContextHolder.getContext().getClientIp();
        LoginResponse response = authService.loginByVerifyCode(request, clientIp);
        return success(response, "登录成功");
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    @IgnoreAuth
    public Result<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refreshToken(request);
        return success(response);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    @RequireLogin
    public Result<Void> logout() {
        authService.logout(getCurrentUserId(), getCurrentSessionId());
        return success(null, "登出成功");
    }

    @Operation(summary = "登出全部设备")
    @PostMapping("/logout-all")
    @RequireLogin
    public Result<Void> logoutAll() {
        authService.logoutAll(getCurrentUserId());
        return success(null, "已登出全部设备");
    }

    @Operation(summary = "切换工作空间并重新签发令牌")
    @PostMapping("/workspaces/{workspaceId}/switch")
    @RequireLogin
    public Result<TokenResponse> switchWorkspace(
            @PathVariable String workspaceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        String userId = getCurrentUserId();
        String clientIp = UserContextHolder.getClientIp();
        TokenResponse response = authService.switchWorkspace(userId, workspaceId, clientIp, userAgent);
        return success(response, "切换工作空间成功");
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    @RequireLogin
    public Result<UserResponse> getCurrentUser() {
        UserResponse response = authService.getCurrentUser(getCurrentUserId());
        return success(response);
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    @IgnoreAuth
    public Result<Map<String, String>> getCaptcha() {
        AuthService.CaptchaResult result = authService.generateCaptcha();
        return success(Map.of(
                "captchaKey", result.captchaKey(),
                "captchaImage", result.captchaImage()
        ));
    }
}
