package com.actionow.user.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.web.controller.BaseController;
import com.actionow.user.dto.request.OAuthCallbackRequest;
import com.actionow.user.dto.response.LoginConfigResponse;
import com.actionow.user.dto.response.OAuthAuthorizeResponse;
import com.actionow.user.dto.response.OAuthBindingResponse;
import com.actionow.user.dto.response.OAuthLoginResponse;
import com.actionow.user.enums.OAuthProvider;
import com.actionow.user.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OAuth控制器
 *
 * @author Actionow
 */
@Tag(name = "OAuth管理", description = "第三方登录授权相关接口")
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController extends BaseController {

    private final OAuthService oAuthService;

    @Operation(summary = "获取登录配置（登录前公开接口）")
    @GetMapping("/login-config")
    @IgnoreAuth
    public Result<LoginConfigResponse> getLoginConfig() {
        LoginConfigResponse response = oAuthService.getLoginConfig();
        return success(response);
    }

    @Operation(summary = "获取OAuth授权URL")
    @GetMapping("/{provider}/authorize")
    @IgnoreAuth
    public Result<OAuthAuthorizeResponse> getAuthorizeUrl(
            @Parameter(description = "OAuth提供商: wechat/github/google/apple")
            @PathVariable String provider,
            @Parameter(description = "回调地址")
            @RequestParam String redirectUri,
            @Parameter(description = "状态码（可选，用于防CSRF）")
            @RequestParam(required = false) String state) {

        OAuthProvider oauthProvider = validateProvider(provider);
        OAuthAuthorizeResponse response = oAuthService.getAuthorizeUrl(oauthProvider, redirectUri, state);
        return success(response);
    }

    @Operation(summary = "OAuth回调处理（登录/注册）")
    @PostMapping("/{provider}/callback")
    @IgnoreAuth
    public Result<OAuthLoginResponse> handleCallback(
            @Parameter(description = "OAuth提供商: wechat/github/google/apple")
            @PathVariable String provider,
            @Valid @RequestBody OAuthCallbackRequest request) {

        OAuthProvider oauthProvider = validateProvider(provider);
        String clientIp = UserContextHolder.getContext().getClientIp();
        OAuthLoginResponse response = oAuthService.handleCallback(
                oauthProvider, request.getCode(), request.getState(),
                request.getRedirectUri(), request.getInviteCode(), clientIp);
        return success(response, response.getIsNewUser() ? "注册成功" : "登录成功");
    }

    @Operation(summary = "绑定第三方账号")
    @PostMapping("/{provider}/bind")
    @RequireLogin
    public Result<Void> bindOAuth(
            @Parameter(description = "OAuth提供商: wechat/github/google/apple")
            @PathVariable String provider,
            @Valid @RequestBody OAuthCallbackRequest request) {

        OAuthProvider oauthProvider = validateProvider(provider);
        oAuthService.bindOAuth(getCurrentUserId(), oauthProvider, request.getCode(), request.getState(), request.getRedirectUri());
        return success(null, "绑定成功");
    }

    @Operation(summary = "解绑第三方账号")
    @DeleteMapping("/{provider}")
    @RequireLogin
    public Result<Void> unbindOAuth(
            @Parameter(description = "OAuth提供商: wechat/github/google/apple")
            @PathVariable String provider) {

        OAuthProvider oauthProvider = validateProvider(provider);
        oAuthService.unbindOAuth(getCurrentUserId(), oauthProvider);
        return success(null, "解绑成功");
    }

    @Operation(summary = "获取用户的OAuth绑定列表")
    @GetMapping("/bindings")
    @RequireLogin
    public Result<List<OAuthBindingResponse>> getOAuthBindings() {
        List<OAuthBindingResponse> bindings = oAuthService.getOAuthBindings(getCurrentUserId());
        return success(bindings);
    }

    /**
     * 验证并获取OAuth提供商
     */
    private OAuthProvider validateProvider(String provider) {
        OAuthProvider oauthProvider = OAuthProvider.fromCode(provider);
        if (oauthProvider == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的OAuth提供商: " + provider);
        }
        return oauthProvider;
    }
}
