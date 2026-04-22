package com.actionow.user.service;

import com.actionow.user.dto.request.*;
import com.actionow.user.dto.response.LoginResponse;
import com.actionow.user.dto.response.SendVerifyCodeResponse;
import com.actionow.user.dto.response.UserResponse;
import com.actionow.user.entity.User;
import com.actionow.common.security.jwt.TokenResponse;

/**
 * 认证服务接口
 *
 * @author Actionow
 */
public interface AuthService {

    /**
     * 发送验证码
     *
     * @param request 发送验证码请求
     * @return 验证码响应
     */
    SendVerifyCodeResponse sendVerifyCode(SendVerifyCodeRequest request);

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 登录响应（自动登录）
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 用户登录（密码）
     *
     * @param request 登录请求
     * @param clientIp 客户端IP
     * @return 登录响应
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * 用户登录（验证码）
     *
     * @param request 验证码登录请求
     * @param clientIp 客户端IP
     * @return 登录响应
     */
    LoginResponse loginByVerifyCode(VerifyCodeLoginRequest request, String clientIp);

    /**
     * 刷新令牌
     *
     * @param request 刷新请求
     * @return Token响应
     */
    TokenResponse refreshToken(RefreshTokenRequest request);

    /**
     * 登出当前会话
     *
     * @param userId 用户ID
     * @param sessionId 当前会话ID（若为null则撤销所有会话）
     */
    void logout(String userId, String sessionId);

    /**
     * 登出全部设备
     *
     * @param userId 用户ID
     */
    void logoutAll(String userId);

    /**
     * 切换工作空间并重新签发 token
     *
     * @param userId 用户ID
     * @param workspaceId 目标工作空间ID
     * @param clientIp 客户端IP
     * @param userAgent 客户端UA
     * @return 新Token
     */
    TokenResponse switchWorkspace(String userId, String workspaceId, String clientIp, String userAgent);

    /**
     * 获取当前用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserResponse getCurrentUser(String userId);

    /**
     * 生成图形验证码
     *
     * @return 验证码Key和图片Base64
     */
    CaptchaResult generateCaptcha();

    /**
     * 修改密码
     *
     * @param userId 用户ID
     * @param request 修改密码请求
     */
    void changePassword(String userId, ChangePasswordRequest request);

    /**
     * 重置密码
     *
     * @param request 重置密码请求
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * 为用户签发登录Token（含AuthSession和RefreshToken）
     * 供OAuth等外部登录流程复用，确保所有登录方式共享同一套会话管理
     *
     * @param user 用户实体
     * @param clientIp 客户端IP
     * @param userAgent 客户端UA
     * @return Token响应
     */
    TokenResponse issueLoginToken(User user, String clientIp, String userAgent);

    /**
     * 验证码结果
     */
    record CaptchaResult(String captchaKey, String captchaImage) {}
}
