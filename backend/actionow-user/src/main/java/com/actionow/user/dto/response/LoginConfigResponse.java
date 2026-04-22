package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录配置响应（登录前公开接口）
 * 包含所有可用的登录方式及 OAuth 提供商信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginConfigResponse {

    /**
     * 是否启用密码登录
     */
    private Boolean passwordLoginEnabled;

    /**
     * 是否启用验证码登录
     */
    private Boolean codeLoginEnabled;

    /**
     * 是否需要邀请码注册
     */
    private Boolean invitationCodeRequired;

    /**
     * 是否允许使用用户邀请码
     */
    private Boolean allowUserCode;

    /**
     * 已启用的 OAuth 提供商列表
     */
    private List<OAuthProviderInfo> oauthProviders;

    /**
     * OAuth 提供商信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OAuthProviderInfo {
        /**
         * 提供商代码 (github, google, linux_do 等)
         */
        private String provider;

        /**
         * 显示名称
         */
        private String displayName;

        /**
         * 图标标识
         */
        private String icon;

        /**
         * 授权地址
         */
        private String authorizeUrl;

        /**
         * 授权范围
         */
        private String scope;

        /**
         * 客户端 ID (公开字段，前端构建授权 URL 需要)
         */
        private String clientId;
    }
}
