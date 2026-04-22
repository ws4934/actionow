package com.actionow.user.service;

import com.actionow.user.dto.response.LoginConfigResponse;
import com.actionow.user.dto.response.OAuthAuthorizeResponse;
import com.actionow.user.dto.response.OAuthBindingResponse;
import com.actionow.user.dto.response.OAuthLoginResponse;
import com.actionow.user.enums.OAuthProvider;

import java.util.List;

/**
 * OAuth服务接口
 *
 * @author Actionow
 */
public interface OAuthService {

    /**
     * 获取授权URL
     *
     * @param provider    OAuth提供商
     * @param redirectUri 回调地址
     * @param state       状态码（可选，用于防CSRF）
     * @return 授权URL响应
     */
    OAuthAuthorizeResponse getAuthorizeUrl(OAuthProvider provider, String redirectUri, String state);

    /**
     * OAuth回调处理（登录/注册）
     *
     * @param provider    OAuth提供商
     * @param code        授权码
     * @param state       状态码
     * @param redirectUri 回调地址（Google OAuth需要）
     * @param inviteCode  邀请码（新用户注册时使用，可选）
     * @param clientIp    客户端IP
     * @return OAuth登录响应
     */
    OAuthLoginResponse handleCallback(OAuthProvider provider, String code, String state,
                                      String redirectUri, String inviteCode, String clientIp);

    /**
     * 绑定第三方账号（已登录用户）
     *
     * @param userId      用户ID
     * @param provider    OAuth提供商
     * @param code        授权码
     * @param state       状态码
     * @param redirectUri 回调地址（Google OAuth需要）
     */
    void bindOAuth(String userId, OAuthProvider provider, String code, String state, String redirectUri);

    /**
     * 解绑第三方账号
     *
     * @param userId   用户ID
     * @param provider OAuth提供商
     */
    void unbindOAuth(String userId, OAuthProvider provider);

    /**
     * 获取用户的OAuth绑定列表
     *
     * @param userId 用户ID
     * @return OAuth绑定列表
     */
    List<OAuthBindingResponse> getOAuthBindings(String userId);

    /**
     * 检查用户是否可以解绑OAuth
     * 需要保留至少一种登录方式（密码或其他OAuth）
     *
     * @param userId   用户ID
     * @param provider OAuth提供商
     * @return 是否可以解绑
     */
    boolean canUnbind(String userId, OAuthProvider provider);

    /**
     * 获取登录配置（登录前公开接口）
     * 包含可用的登录方式和已启用的 OAuth 提供商列表
     *
     * @return 登录配置响应
     */
    LoginConfigResponse getLoginConfig();
}
