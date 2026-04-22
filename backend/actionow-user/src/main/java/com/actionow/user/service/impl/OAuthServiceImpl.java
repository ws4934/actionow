package com.actionow.user.service.impl;

import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.redis.service.RedisCacheService;
import com.actionow.common.security.jwt.TokenResponse;
import com.actionow.user.config.UserRuntimeConfigService;
import com.actionow.user.dto.response.LoginConfigResponse;
import com.actionow.user.dto.response.OAuthAuthorizeResponse;
import com.actionow.user.dto.response.OAuthBindingResponse;
import com.actionow.user.dto.response.OAuthLoginResponse;
import com.actionow.user.dto.response.InvitationCodeValidateResponse;
import com.actionow.user.entity.User;
import com.actionow.user.entity.UserOauth;
import com.actionow.user.enums.OAuthProvider;
import com.actionow.user.enums.UserErrorCode;
import com.actionow.user.enums.UserStatus;
import com.actionow.user.mapper.UserOauthMapper;
import com.actionow.user.service.AuthService;
import com.actionow.user.service.InvitationCodeService;
import com.actionow.user.service.OAuthService;
import com.actionow.user.service.UserMailHelper;
import com.actionow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * OAuth服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements OAuthService {

    private final UserRuntimeConfigService runtimeConfig;
    private final UserOauthMapper userOauthMapper;
    private final UserService userService;
    private final AuthService authService;
    private final InvitationCodeService invitationCodeService;
    private final UserMailHelper userMailHelper;
    private final RedisCacheService redisCacheService;
    private final RestTemplate restTemplate;

    @Override
    public OAuthAuthorizeResponse getAuthorizeUrl(OAuthProvider provider, String redirectUri, String state) {
        validateSupportedProvider(provider);
        UserRuntimeConfigService.ProviderConfig config = getProviderConfig(provider);

        // 如果没有传入state，则生成一个
        if (state == null || state.isEmpty()) {
            state = UuidGenerator.generateShortId();
        }

        // 缓存state以便后续验证
        redisCacheService.set(
                RedisKeyConstants.OAUTH_STATE + state,
                redirectUri,
                RedisKeyConstants.TTL_OAUTH_STATE,
                TimeUnit.SECONDS
        );

        // 构建授权URL
        String authorizeUrl = buildAuthorizeUrl(provider, config, redirectUri, state);

        return OAuthAuthorizeResponse.builder()
                .authorizeUrl(authorizeUrl)
                .state(state)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuthLoginResponse handleCallback(OAuthProvider provider, String code, String state,
                                             String redirectUri, String inviteCode, String clientIp) {
        // 验证state
        validateState(state);

        // 获取OAuth用户信息
        OAuthUserInfo oAuthUserInfo = getOAuthUserInfo(provider, code, redirectUri);

        // 查找是否已绑定
        UserOauth existingOauth = userOauthMapper.selectByProviderAndProviderId(
                provider.getCode(), oAuthUserInfo.getOpenId());

        boolean isNewUser = false;
        User user;

        if (existingOauth != null) {
            // 已绑定，直接登录
            user = userService.getById(existingOauth.getUserId());
            if (user == null) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
            UserStatus status = UserStatus.fromCode(user.getStatus());
            if (status == null || !status.canLogin()) {
                throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
            }
            // 更新OAuth信息
            updateOAuthInfo(existingOauth, oAuthUserInfo);
            // 同步更新用户头像（如果OAuth头像有变化）
            if (oAuthUserInfo.getAvatar() != null && !oAuthUserInfo.getAvatar().equals(user.getAvatar())) {
                user.setAvatar(oAuthUserInfo.getAvatar());
                userService.updateById(user);
            }
        } else {
            // 未绑定，需要创建新用户
            isNewUser = true;

            // 邀请码验证
            String inviterId = null;
            boolean invitationRequired = invitationCodeService.isInvitationCodeRequired();

            if (invitationRequired) {
                // 系统要求邀请码
                if (inviteCode == null || inviteCode.isEmpty()) {
                    throw new BusinessException(ResultCode.INVITATION_CODE_REQUIRED);
                }
            }

            // 如果提供了邀请码，进行验证
            if (inviteCode != null && !inviteCode.isEmpty()) {
                InvitationCodeValidateResponse validateResult = invitationCodeService.validateCode(inviteCode);
                if (!validateResult.getValid()) {
                    throw new BusinessException(ResultCode.INVITATION_CODE_INVALID, validateResult.getMessage());
                }
                // 获取邀请人ID（如果是用户邀请码）
                if ("User".equals(validateResult.getType())) {
                    var invitationCode = invitationCodeService.findByCode(inviteCode);
                    if (invitationCode != null) {
                        inviterId = invitationCode.getOwnerId();
                    }
                }
            }

            // 创建用户
            user = createUserFromOAuth(provider, oAuthUserInfo, inviterId, inviteCode);
            createOAuthBinding(user.getId(), provider, oAuthUserInfo);

            // 记录邀请码使用
            if (inviteCode != null && !inviteCode.isEmpty()) {
                invitationCodeService.useCode(inviteCode, user, clientIp, null);
            }

            // 为新用户生成专属邀请码
            if (invitationCodeService.isUserCodeEnabled()) {
                try {
                    invitationCodeService.generateUserCode(user.getId());
                } catch (Exception e) {
                    log.warn("为OAuth用户生成邀请码失败: userId={}, error={}", user.getId(), e.getMessage());
                }
            }

            // 异步发送欢迎邮件
            userMailHelper.sendWelcomeEmailAsync(user);
        }

        // 更新最后登录信息
        userService.updateLastLogin(user.getId(), clientIp);

        // 生成Token（复用AuthService，含AuthSession和RefreshToken Rotation）
        TokenResponse token = authService.issueLoginToken(user, clientIp, null);

        log.info("OAuth登录成功: provider={}, userId={}, isNewUser={}", provider.getCode(), user.getId(), isNewUser);

        return OAuthLoginResponse.builder()
                .isNewUser(isNewUser)
                .user(userService.toResponse(user))
                .token(token)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindOAuth(String userId, OAuthProvider provider, String code, String state, String redirectUri) {
        // 验证state
        validateState(state);

        // 检查用户是否存在
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查是否已绑定该提供商
        UserOauth existingBinding = userOauthMapper.selectByUserIdAndProvider(userId, provider.getCode());
        if (existingBinding != null) {
            throw new BusinessException(ResultCode.OAUTH_BINDINGEXISTS, "您已绑定该第三方账号");
        }

        // 获取OAuth用户信息
        OAuthUserInfo oAuthUserInfo = getOAuthUserInfo(provider, code, redirectUri);

        // 检查该OAuth账号是否已被其他用户绑定
        UserOauth otherBinding = userOauthMapper.selectByProviderAndProviderId(
                provider.getCode(), oAuthUserInfo.getOpenId());
        if (otherBinding != null) {
            throw new BusinessException(ResultCode.OAUTH_BINDINGEXISTS, "该第三方账号已绑定其他用户");
        }

        // 创建绑定
        createOAuthBinding(userId, provider, oAuthUserInfo);

        log.info("OAuth绑定成功: userId={}, provider={}", userId, provider.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindOAuth(String userId, OAuthProvider provider) {
        // 检查是否可以解绑
        if (!canUnbind(userId, provider)) {
            throw new BusinessException(ResultCode.CANNOT_UNBIND_LAST);
        }

        // 查找绑定记录
        UserOauth oauth = userOauthMapper.selectByUserIdAndProvider(userId, provider.getCode());
        if (oauth == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到该绑定记录");
        }

        // 删除绑定（软删除）
        userOauthMapper.deleteById(oauth.getId());

        log.info("OAuth解绑成功: userId={}, provider={}", userId, provider.getCode());
    }

    @Override
    public List<OAuthBindingResponse> getOAuthBindings(String userId) {
        List<UserOauth> oauthList = userOauthMapper.selectByUserId(userId);
        return oauthList.stream()
                .map(oauth -> OAuthBindingResponse.builder()
                        .provider(oauth.getProvider())
                        .providerUsername(oauth.getProviderUsername())
                        .providerEmail(oauth.getProviderEmail())
                        .providerAvatar(oauth.getProviderAvatar())
                        .bindAt(oauth.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public boolean canUnbind(String userId, OAuthProvider provider) {
        User user = userService.getById(userId);
        if (user == null) {
            return false;
        }

        // 检查是否有密码
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();

        // 统计OAuth绑定数量
        int oauthCount = userOauthMapper.countByUserId(userId);

        // 如果有密码，可以解绑任意OAuth
        // 如果没有密码，必须保留至少一个OAuth绑定
        return hasPassword || oauthCount > 1;
    }

    @Override
    public LoginConfigResponse getLoginConfig() {
        // 获取所有启用且已配置 clientId 的 OAuth 提供商信息
        List<LoginConfigResponse.OAuthProviderInfo> providers = runtimeConfig.getEnabledProviders().stream()
                .map(code -> {
                    UserRuntimeConfigService.ProviderConfig config = runtimeConfig.getProviderConfig(code);
                    return LoginConfigResponse.OAuthProviderInfo.builder()
                            .provider(code)
                            .displayName(config.getDisplayName())
                            .icon(config.getIcon())
                            .authorizeUrl(config.getAuthorizeUrl())
                            .scope(config.getScope())
                            .clientId(config.getClientId())
                            .build();
                })
                .filter(p -> p.getClientId() != null && !p.getClientId().isEmpty())
                .collect(Collectors.toList());

        return LoginConfigResponse.builder()
                .passwordLoginEnabled(runtimeConfig.isPasswordLoginEnabled())
                .codeLoginEnabled(runtimeConfig.isCodeLoginEnabled())
                .invitationCodeRequired(invitationCodeService.isInvitationCodeRequired())
                .allowUserCode(invitationCodeService.isUserCodeAllowed())
                .oauthProviders(providers)
                .build();
    }

    /**
     * 校验 OAuth 提供商是否已启用
     */
    private void validateSupportedProvider(OAuthProvider provider) {
        if (!runtimeConfig.isProviderEnabled(provider.getCode())) {
            throw new BusinessException(UserErrorCode.OAUTH_PROVIDER_INVALID,
                    "该OAuth提供商未启用: " + provider.getName());
        }
    }

    /**
     * 获取提供商配置
     */
    private UserRuntimeConfigService.ProviderConfig getProviderConfig(OAuthProvider provider) {
        UserRuntimeConfigService.ProviderConfig config = runtimeConfig.getProviderConfig(provider.getCode());
        if (config.getClientId() == null || config.getClientId().isEmpty()) {
            throw new BusinessException(UserErrorCode.OAUTH_PROVIDER_INVALID,
                    "OAuth提供商未配置 Client ID: " + provider.getCode());
        }
        return config;
    }

    /**
     * 构建授权URL
     */
    private String buildAuthorizeUrl(OAuthProvider provider, UserRuntimeConfigService.ProviderConfig config,
                                     String redirectUri, String state) {
        String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        return switch (provider) {
            case GITHUB -> String.format(
                    "%s?client_id=%s&redirect_uri=%s&state=%s&scope=%s",
                    config.getAuthorizeUrl(),
                    config.getClientId(),
                    encodedRedirectUri,
                    state,
                    config.getScope()
            );
            case GOOGLE -> String.format(
                    "%s?client_id=%s&redirect_uri=%s&state=%s&scope=%s&response_type=code&access_type=offline",
                    config.getAuthorizeUrl(),
                    config.getClientId(),
                    encodedRedirectUri,
                    state,
                    URLEncoder.encode(config.getScope(), StandardCharsets.UTF_8)
            );
            case WECHAT -> String.format(
                    "%s?appid=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s#wechat_redirect",
                    config.getAuthorizeUrl(),
                    config.getClientId(),
                    encodedRedirectUri,
                    config.getScope(),
                    state
            );
            case APPLE -> String.format(
                    "%s?client_id=%s&redirect_uri=%s&state=%s&scope=%s&response_type=code&response_mode=form_post",
                    config.getAuthorizeUrl(),
                    config.getClientId(),
                    encodedRedirectUri,
                    state,
                    URLEncoder.encode(config.getScope(), StandardCharsets.UTF_8)
            );
            case LINUX_DO -> String.format(
                    "%s?client_id=%s&redirect_uri=%s&state=%s&scope=%s&response_type=code",
                    config.getAuthorizeUrl(),
                    config.getClientId(),
                    encodedRedirectUri,
                    state,
                    URLEncoder.encode(config.getScope(), StandardCharsets.UTF_8)
            );
        };
    }

    /**
     * 验证state（CSRF防护，强制要求）
     */
    private void validateState(String state) {
        if (state == null || state.isEmpty()) {
            throw new BusinessException(UserErrorCode.OAUTH_STATE_INVALID, "缺少state参数");
        }
        String cachedRedirectUri = redisCacheService.get(RedisKeyConstants.OAUTH_STATE + state);
        if (cachedRedirectUri == null) {
            throw new BusinessException(UserErrorCode.OAUTH_STATE_INVALID, "无效或已过期的state参数");
        }
        // 删除已使用的state
        redisCacheService.delete(RedisKeyConstants.OAUTH_STATE + state);
    }

    /**
     * 获取OAuth用户信息
     */
    private OAuthUserInfo getOAuthUserInfo(OAuthProvider provider, String code, String redirectUri) {
        UserRuntimeConfigService.ProviderConfig config = getProviderConfig(provider);
        log.info("获取OAuth用户信息: provider={}", provider.getCode());

        return switch (provider) {
            case GITHUB -> getGitHubUserInfo(config, code);
            case GOOGLE -> getGoogleUserInfo(config, code, redirectUri);
            case LINUX_DO -> getLinuxDoUserInfo(config, code, redirectUri);
            default -> throw new BusinessException(UserErrorCode.OAUTH_PROVIDER_INVALID,
                    "暂不支持的OAuth提供商: " + provider.getCode());
        };
    }

    /**
     * GitHub OAuth: 获取用户信息
     */
    private OAuthUserInfo getGitHubUserInfo(UserRuntimeConfigService.ProviderConfig config, String code) {
        // Step 1: 用code换取access_token
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        tokenHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
        tokenParams.add("client_id", config.getClientId());
        tokenParams.add("client_secret", config.getClientSecret());
        tokenParams.add("code", code);

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenParams, tokenHeaders);

        Map<String, Object> tokenResponse;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    config.getTokenUrl(),
                    HttpMethod.POST,
                    tokenRequest,
                    new ParameterizedTypeReference<>() {}
            );
            tokenResponse = response.getBody();
        } catch (RestClientException e) {
            log.error("GitHub获取access_token失败: {}", e.getMessage());
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "GitHub授权码无效或已过期");
        }

        if (tokenResponse == null || tokenResponse.containsKey("error")) {
            String error = tokenResponse != null ? String.valueOf(tokenResponse.get("error_description")) : "未知错误";
            log.error("GitHub返回错误: {}", error);
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "GitHub授权失败: " + error);
        }

        String accessToken = (String) tokenResponse.get("access_token");
        if (accessToken == null) {
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "GitHub未返回access_token");
        }

        // Step 2: 用access_token获取用户信息
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        userHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        userHeaders.set("User-Agent", "Actionow-OAuth");

        HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);

        Map<String, Object> userInfo;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    config.getUserInfoUrl(),
                    HttpMethod.GET,
                    userRequest,
                    new ParameterizedTypeReference<>() {}
            );
            userInfo = response.getBody();
        } catch (RestClientException e) {
            log.error("GitHub获取用户信息失败: {}", e.getMessage());
            throw new BusinessException(UserErrorCode.OAUTH_USERINFO_FAILED, "获取GitHub用户信息失败");
        }

        if (userInfo == null) {
            throw new BusinessException(UserErrorCode.OAUTH_USERINFO_FAILED, "GitHub用户信息为空");
        }

        // Step 3: 获取用户邮箱（可能需要额外请求）
        String email = (String) userInfo.get("email");
        if (email == null) {
            email = getGitHubPrimaryEmail(accessToken);
        }

        log.info("GitHub用户信息获取成功: id={}, login={}", userInfo.get("id"), userInfo.get("login"));

        return OAuthUserInfo.builder()
                .openId(String.valueOf(userInfo.get("id")))
                .nickname((String) userInfo.get("name"))
                .avatar((String) userInfo.get("avatar_url"))
                .email(email)
                .accessToken(accessToken)
                .rawInfo(userInfo)
                .build();
    }

    /**
     * GitHub: 获取用户主邮箱
     */
    private String getGitHubPrimaryEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "Actionow-OAuth");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    "https://api.github.com/user/emails",
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> emails = response.getBody();
            if (emails != null) {
                // 优先返回已验证的主邮箱
                for (Map<String, Object> emailInfo : emails) {
                    Boolean primary = (Boolean) emailInfo.get("primary");
                    Boolean verified = (Boolean) emailInfo.get("verified");
                    if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                        return (String) emailInfo.get("email");
                    }
                }
                // 其次返回任意已验证邮箱
                for (Map<String, Object> emailInfo : emails) {
                    Boolean verified = (Boolean) emailInfo.get("verified");
                    if (Boolean.TRUE.equals(verified)) {
                        return (String) emailInfo.get("email");
                    }
                }
            }
        } catch (RestClientException e) {
            log.warn("GitHub获取邮箱失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Google OAuth: 获取用户信息
     */
    private OAuthUserInfo getGoogleUserInfo(UserRuntimeConfigService.ProviderConfig config, String code, String redirectUri) {
        // Step 1: 用code换取access_token
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
        tokenParams.add("client_id", config.getClientId());
        tokenParams.add("client_secret", config.getClientSecret());
        tokenParams.add("code", code);
        tokenParams.add("grant_type", "authorization_code");
        // redirect_uri 必须与获取授权URL时一致
        String effectiveRedirectUri;
        if (redirectUri != null && !redirectUri.isEmpty()) {
            effectiveRedirectUri = redirectUri;
        } else {
            // 如果前端使用 Google JS SDK，可以使用 postmessage
            effectiveRedirectUri = "postmessage";
        }
        tokenParams.add("redirect_uri", effectiveRedirectUri);
        log.info("Google OAuth token exchange: client_id={}, redirect_uri={}", config.getClientId(), effectiveRedirectUri);

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenParams, tokenHeaders);

        Map<String, Object> tokenResponse;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    config.getTokenUrl(),
                    HttpMethod.POST,
                    tokenRequest,
                    new ParameterizedTypeReference<>() {}
            );
            tokenResponse = response.getBody();
        } catch (RestClientException e) {
            log.error("Google获取access_token失败: {}", e.getMessage());
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "Google授权码无效或已过期");
        }

        if (tokenResponse == null || tokenResponse.containsKey("error")) {
            String error = tokenResponse != null ? String.valueOf(tokenResponse.get("error_description")) : "未知错误";
            log.error("Google返回错误: {}", error);
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "Google授权失败: " + error);
        }

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");
        Integer expiresIn = tokenResponse.get("expires_in") != null
                ? ((Number) tokenResponse.get("expires_in")).intValue() : null;

        if (accessToken == null) {
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "Google未返回access_token");
        }

        // Step 2: 用access_token获取用户信息
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);

        HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);

        Map<String, Object> userInfo;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    config.getUserInfoUrl(),
                    HttpMethod.GET,
                    userRequest,
                    new ParameterizedTypeReference<>() {}
            );
            userInfo = response.getBody();
        } catch (RestClientException e) {
            log.error("Google获取用户信息失败: {}", e.getMessage());
            throw new BusinessException(UserErrorCode.OAUTH_USERINFO_FAILED, "获取Google用户信息失败");
        }

        if (userInfo == null) {
            throw new BusinessException(UserErrorCode.OAUTH_USERINFO_FAILED, "Google用户信息为空");
        }

        log.info("Google用户信息获取成功: sub={}, email={}", userInfo.get("sub"), userInfo.get("email"));

        return OAuthUserInfo.builder()
                .openId((String) userInfo.get("sub"))
                .nickname((String) userInfo.get("name"))
                .avatar((String) userInfo.get("picture"))
                .email((String) userInfo.get("email"))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .rawInfo(userInfo)
                .build();
    }

    /**
     * Linux.do (Discourse) OAuth2: 获取用户信息
     */
    private OAuthUserInfo getLinuxDoUserInfo(UserRuntimeConfigService.ProviderConfig config, String code, String redirectUri) {
        // Step 1: 用code换取access_token（使用 Basic Auth）
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        tokenHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        String credentials = Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        tokenHeaders.set("Authorization", "Basic " + credentials);

        MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
        tokenParams.add("grant_type", "authorization_code");
        tokenParams.add("code", code);
        if (redirectUri != null && !redirectUri.isEmpty()) {
            tokenParams.add("redirect_uri", redirectUri);
        }

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenParams, tokenHeaders);

        Map<String, Object> tokenResponse;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    config.getTokenUrl(),
                    HttpMethod.POST,
                    tokenRequest,
                    new ParameterizedTypeReference<>() {}
            );
            tokenResponse = response.getBody();
        } catch (RestClientException e) {
            log.error("Linux.do获取access_token失败: {}", e.getMessage());
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "Linux.do授权码无效或已过期");
        }

        if (tokenResponse == null || tokenResponse.containsKey("error")) {
            String error = tokenResponse != null ? String.valueOf(tokenResponse.get("error_description")) : "未知错误";
            log.error("Linux.do返回错误: {}", error);
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "Linux.do授权失败: " + error);
        }

        String accessToken = (String) tokenResponse.get("access_token");
        if (accessToken == null) {
            throw new BusinessException(UserErrorCode.OAUTH_CODE_INVALID, "Linux.do未返回access_token");
        }

        // Step 2: 用access_token获取用户信息
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        userHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);

        Map<String, Object> userInfo;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    config.getUserInfoUrl(),
                    HttpMethod.GET,
                    userRequest,
                    new ParameterizedTypeReference<>() {}
            );
            userInfo = response.getBody();
        } catch (RestClientException e) {
            log.error("Linux.do获取用户信息失败: {}", e.getMessage());
            throw new BusinessException(UserErrorCode.OAUTH_USERINFO_FAILED, "获取Linux.do用户信息失败");
        }

        if (userInfo == null) {
            throw new BusinessException(UserErrorCode.OAUTH_USERINFO_FAILED, "Linux.do用户信息为空");
        }

        log.info("Linux.do用户信息获取成功: id={}, username={}", userInfo.get("id"), userInfo.get("username"));

        // Linux.do (Discourse Connect) 返回字段映射
        // Discourse 返回 name（显示名）和 username（登录名），优先使用 name
        String nickname = (String) userInfo.get("name");
        if (nickname == null || nickname.isBlank()) {
            nickname = (String) userInfo.get("username");
        }

        String avatarUrl = (String) userInfo.get("avatar_url");
        // Discourse 的 avatar_url 可能是相对路径，需要拼接基础域名
        if (avatarUrl != null && !avatarUrl.startsWith("http")) {
            avatarUrl = "https://linux.do" + avatarUrl;
        }

        return OAuthUserInfo.builder()
                .openId(String.valueOf(userInfo.get("id")))
                .nickname(nickname)
                .avatar(avatarUrl)
                .email((String) userInfo.get("email"))
                .accessToken(accessToken)
                .rawInfo(userInfo)
                .build();
    }

    /**
     * 从OAuth信息创建用户
     */
    private User createUserFromOAuth(OAuthProvider provider, OAuthUserInfo oAuthUserInfo,
                                     String invitedBy, String invitationCodeUsed) {
        // 生成唯一用户名
        String username = provider.getCode() + "_" + UuidGenerator.generateShortId();
        String nickname = oAuthUserInfo.getNickname() != null ?
                oAuthUserInfo.getNickname() : username;

        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setAvatar(oAuthUserInfo.getAvatar());
        user.setEmail(oAuthUserInfo.getEmail());
        user.setStatus(UserStatus.ACTIVE.getCode());
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setLoginFailCount(0);
        user.setInvitedBy(invitedBy);
        user.setInvitationCodeUsed(invitationCodeUsed);

        userService.save(user);
        log.info("通过OAuth创建用户: userId={}, username={}, invitedBy={}", user.getId(), username, invitedBy);
        return user;
    }

    /**
     * 创建OAuth绑定
     */
    private void createOAuthBinding(String userId, OAuthProvider provider, OAuthUserInfo oAuthUserInfo) {
        UserOauth oauth = new UserOauth();
        oauth.setUserId(userId);
        oauth.setProvider(provider.getCode());
        oauth.setProviderUserId(oAuthUserInfo.getOpenId());
        oauth.setUnionId(oAuthUserInfo.getUnionId());
        oauth.setProviderUsername(oAuthUserInfo.getNickname());
        oauth.setProviderEmail(oAuthUserInfo.getEmail());
        oauth.setProviderAvatar(oAuthUserInfo.getAvatar());
        oauth.setAccessToken(oAuthUserInfo.getAccessToken());
        oauth.setRefreshToken(oAuthUserInfo.getRefreshToken());
        oauth.setExpiresIn(oAuthUserInfo.getExpiresIn());
        if (oAuthUserInfo.getExpiresIn() != null) {
            oauth.setTokenExpiresAt(LocalDateTime.now().plusSeconds(oAuthUserInfo.getExpiresIn()));
        }
        oauth.setExtraInfo(oAuthUserInfo.getRawInfo());

        userOauthMapper.insert(oauth);
    }

    /**
     * 更新OAuth信息
     */
    private void updateOAuthInfo(UserOauth oauth, OAuthUserInfo oAuthUserInfo) {
        oauth.setProviderUsername(oAuthUserInfo.getNickname());
        oauth.setProviderEmail(oAuthUserInfo.getEmail());
        oauth.setProviderAvatar(oAuthUserInfo.getAvatar());
        oauth.setAccessToken(oAuthUserInfo.getAccessToken());
        oauth.setRefreshToken(oAuthUserInfo.getRefreshToken());
        oauth.setExpiresIn(oAuthUserInfo.getExpiresIn());
        if (oAuthUserInfo.getExpiresIn() != null) {
            oauth.setTokenExpiresAt(LocalDateTime.now().plusSeconds(oAuthUserInfo.getExpiresIn()));
        }
        oauth.setExtraInfo(oAuthUserInfo.getRawInfo());

        userOauthMapper.updateById(oauth);
    }

    /**
     * OAuth用户信息内部类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class OAuthUserInfo {
        private String openId;
        private String unionId;
        private String nickname;
        private String avatar;
        private String email;
        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;
        private Map<String, Object> rawInfo;
    }

}
