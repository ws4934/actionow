package com.actionow.user.config;

import com.actionow.common.redis.config.RuntimeConfigService;
import com.actionow.user.enums.OAuthProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User 模块运行时配置服务
 * 管理 OAuth 提供商配置 和 登录方式开关
 *
 * @author Actionow
 */
@Slf4j
@Component
public class UserRuntimeConfigService extends RuntimeConfigService {

    // ==================== 登录方式开关 ====================

    public static final String PASSWORD_LOGIN_ENABLED = "auth.login.password_enabled";
    public static final String CODE_LOGIN_ENABLED     = "auth.login.code_enabled";

    // ==================== OAuth 配置键模板 ====================

    private static final String OAUTH_PREFIX = "oauth.";
    private static final String SUFFIX_ENABLED       = ".enabled";
    private static final String SUFFIX_CLIENT_ID     = ".client_id";
    private static final String SUFFIX_CLIENT_SECRET = ".client_secret";
    private static final String SUFFIX_AUTHORIZE_URL = ".authorize_url";
    private static final String SUFFIX_TOKEN_URL     = ".token_url";
    private static final String SUFFIX_USER_INFO_URL = ".user_info_url";
    private static final String SUFFIX_SCOPE         = ".scope";
    private static final String SUFFIX_DISPLAY_NAME  = ".display_name";
    private static final String SUFFIX_ICON          = ".icon";

    /** 所有已知 provider codes */
    private static final List<String> PROVIDER_CODES = Arrays.stream(OAuthProvider.values())
            .map(OAuthProvider::getCode)
            .collect(Collectors.toList());

    public UserRuntimeConfigService(StringRedisTemplate redisTemplate,
                                     RedisMessageListenerContainer listenerContainer) {
        super(redisTemplate, listenerContainer);
    }

    @Override
    protected String getPrefix() {
        // 监听 oauth.* 和 auth.login.* 两个前缀的变更
        return "oauth";
    }

    @Override
    protected void registerDefaults(Map<String, String> defaults) {
        // 登录方式开关
        defaults.put(PASSWORD_LOGIN_ENABLED, "true");
        defaults.put(CODE_LOGIN_ENABLED, "true");

        // 为每个 provider 注册默认值
        registerProviderDefaults(defaults, "github", true,
                "https://github.com/login/oauth/authorize",
                "https://github.com/login/oauth/access_token",
                "https://api.github.com/user",
                "read:user,user:email", "GitHub", "github");

        registerProviderDefaults(defaults, "google", true,
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "https://www.googleapis.com/oauth2/v3/userinfo",
                "openid profile email", "Google", "google");

        registerProviderDefaults(defaults, "wechat", false,
                "https://open.weixin.qq.com/connect/qrconnect",
                "https://api.weixin.qq.com/sns/oauth2/access_token",
                "https://api.weixin.qq.com/sns/userinfo",
                "snsapi_login", "微信", "wechat");

        registerProviderDefaults(defaults, "apple", false,
                "https://appleid.apple.com/auth/authorize",
                "https://appleid.apple.com/auth/token",
                "",
                "name email", "Apple", "apple");

        registerProviderDefaults(defaults, "linux_do", true,
                "https://connect.linux.do/oauth2/authorize",
                "https://connect.linux.do/oauth2/token",
                "https://connect.linux.do/api/user",
                "read", "Linux.do", "linux_do");
    }

    private void registerProviderDefaults(Map<String, String> defaults, String code, boolean enabled,
                                           String authorizeUrl, String tokenUrl, String userInfoUrl,
                                           String scope, String displayName, String icon) {
        String prefix = OAUTH_PREFIX + code;
        defaults.put(prefix + SUFFIX_ENABLED,       String.valueOf(enabled));
        defaults.put(prefix + SUFFIX_CLIENT_ID,     "");
        defaults.put(prefix + SUFFIX_CLIENT_SECRET, "");
        defaults.put(prefix + SUFFIX_AUTHORIZE_URL, authorizeUrl);
        defaults.put(prefix + SUFFIX_TOKEN_URL,     tokenUrl);
        defaults.put(prefix + SUFFIX_USER_INFO_URL, userInfoUrl);
        defaults.put(prefix + SUFFIX_SCOPE,         scope);
        defaults.put(prefix + SUFFIX_DISPLAY_NAME,  displayName);
        defaults.put(prefix + SUFFIX_ICON,          icon);
    }

    @Override
    protected void onConfigChanged(String key, String oldValue, String newValue) {
        // auth.login.* 也需要监听
        if (key.startsWith("auth.login.")) {
            log.info("[UserRuntimeConfig] Login config changed: {}={}", key, newValue);
        }
    }

    // ==================== 便捷方法 ====================

    public boolean isPasswordLoginEnabled() {
        return getBoolean(PASSWORD_LOGIN_ENABLED);
    }

    public boolean isCodeLoginEnabled() {
        return getBoolean(CODE_LOGIN_ENABLED);
    }

    /**
     * 检查指定 provider 是否启用
     */
    public boolean isProviderEnabled(String providerCode) {
        return getBoolean(OAUTH_PREFIX + providerCode + SUFFIX_ENABLED);
    }

    /**
     * 获取指定 provider 的完整配置
     */
    public ProviderConfig getProviderConfig(String providerCode) {
        String prefix = OAUTH_PREFIX + providerCode;
        ProviderConfig config = new ProviderConfig();
        config.setClientId(getString(prefix + SUFFIX_CLIENT_ID));
        config.setClientSecret(getString(prefix + SUFFIX_CLIENT_SECRET));
        config.setAuthorizeUrl(getString(prefix + SUFFIX_AUTHORIZE_URL));
        config.setTokenUrl(getString(prefix + SUFFIX_TOKEN_URL));
        config.setUserInfoUrl(getString(prefix + SUFFIX_USER_INFO_URL));
        config.setScope(getString(prefix + SUFFIX_SCOPE));
        config.setDisplayName(getString(prefix + SUFFIX_DISPLAY_NAME));
        config.setIcon(getString(prefix + SUFFIX_ICON));
        return config;
    }

    /**
     * 获取所有已启用的 provider codes
     */
    public List<String> getEnabledProviders() {
        return PROVIDER_CODES.stream()
                .filter(this::isProviderEnabled)
                .collect(Collectors.toList());
    }

    /**
     * OAuth 提供商配置 DTO
     */
    @Data
    public static class ProviderConfig {
        private String clientId;
        private String clientSecret;
        private String authorizeUrl;
        private String tokenUrl;
        private String userInfoUrl;
        private String scope;
        private String displayName;
        private String icon;
    }
}
