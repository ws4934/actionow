package com.actionow.user.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.util.EncryptUtils;
import com.actionow.common.redis.service.RedisCacheService;
import com.actionow.common.security.config.JwtProperties;
import com.actionow.common.security.jwt.JwtClaims;
import com.actionow.common.security.jwt.JwtUtils;
import com.actionow.common.security.jwt.TokenResponse;
import com.actionow.common.security.workspace.WorkspaceInternalClient;
import com.actionow.common.security.workspace.WorkspaceMembershipInfo;
import com.actionow.user.dto.request.*;
import com.actionow.user.dto.response.InvitationCodeValidateResponse;
import com.actionow.user.dto.response.LoginResponse;
import com.actionow.user.dto.response.SendVerifyCodeResponse;
import com.actionow.user.dto.response.UserResponse;
import com.actionow.user.entity.AuthSession;
import com.actionow.user.entity.RefreshTokenRecord;
import com.actionow.user.entity.User;
import com.actionow.user.enums.UserStatus;
import com.actionow.user.enums.VerifyCodeType;
import com.actionow.user.mapper.AuthSessionMapper;
import com.actionow.user.mapper.RefreshTokenMapper;
import com.actionow.user.service.AuthService;
import com.actionow.user.service.InvitationCodeService;
import com.actionow.user.service.UserMailHelper;
import com.actionow.user.service.UserService;
import com.actionow.user.service.VerifyCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 30;
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    private static final String SESSION_STATUS_REVOKED = "REVOKED";
    private static final String TOKEN_STATUS_ACTIVE = "ACTIVE";
    private static final String TOKEN_STATUS_USED = "USED";
    private static final String REVOKE_REASON_REFRESH_REUSE = "REFRESH_REUSE";
    private static final String REVOKE_REASON_LOGOUT = "LOGOUT";
    private static final String REVOKE_REASON_SWITCH_WORKSPACE = "WORKSPACE_SWITCH";
    private static final String REVOKE_REASON_PASSWORD_CHANGE = "PASSWORD_CHANGE";

    private final UserService userService;
    private final VerifyCodeService verifyCodeService;
    private final InvitationCodeService invitationCodeService;
    private final UserMailHelper userMailHelper;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final RedisCacheService redisCacheService;
    private final WorkspaceInternalClient workspaceInternalClient;
    private final AuthSessionMapper authSessionMapper;
    private final RefreshTokenMapper refreshTokenMapper;

    @Override
    public SendVerifyCodeResponse sendVerifyCode(SendVerifyCodeRequest request) {
        // 验证图形验证码
        validateCaptcha(request.getCaptchaKey(), request.getCaptcha());

        // 解析验证码类型
        VerifyCodeType type = VerifyCodeType.fromCode(request.getType());
        if (type == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "无效的验证码类型");
        }

        // 发送验证码
        int expireIn = verifyCodeService.sendVerifyCode(request.getTarget(), type);

        return SendVerifyCodeResponse.builder()
                .expireIn(expireIn)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        // 验证邮箱或手机号至少填写一个
        if ((request.getEmail() == null || request.getEmail().isEmpty())
                && (request.getPhone() == null || request.getPhone().isEmpty())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱或手机号至少填写一个");
        }

        // 验证验证码
        if (!verifyCodeService.validateAndDeleteVerifyCode(
                request.getTarget(), VerifyCodeType.REGISTER, request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }

        // 邀请码验证
        String inviterId = null;
        String invitationCode = request.getInviteCode();
        boolean invitationRequired = invitationCodeService.isInvitationCodeRequired();

        if (invitationRequired) {
            // 系统要求邀请码
            if (invitationCode == null || invitationCode.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "需要邀请码才能注册");
            }
        }

        // 如果提供了邀请码，进行验证
        if (invitationCode != null && !invitationCode.isEmpty()) {
            InvitationCodeValidateResponse validateResult = invitationCodeService.validateCode(invitationCode);
            if (!validateResult.getValid()) {
                throw new BusinessException(ResultCode.PARAM_INVALID, validateResult.getMessage());
            }
            // 获取邀请人ID（如果是用户邀请码）
            if ("User".equals(validateResult.getType())) {
                var code = invitationCodeService.findByCode(invitationCode);
                if (code != null) {
                    inviterId = code.getOwnerId();
                }
            }
        }

        // 检查用户名是否已存在
        if (userService.existsByUsername(request.getUsername())) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        // 检查邮箱是否已存在
        if (request.getEmail() != null && !request.getEmail().isEmpty()
                && userService.existsByEmail(request.getEmail())) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }

        // 检查手机号是否已存在
        if (request.getPhone() != null && !request.getPhone().isEmpty()
                && userService.existsByPhone(request.getPhone())) {
            throw new BusinessException(ResultCode.PHONE_EXISTS);
        }

        // 创建用户（包含邀请人信息）
        User user = userService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getPhone(),
                request.getPassword(),
                request.getNickname(),
                inviterId,
                invitationCode
        );

        // 记录邀请码使用
        if (invitationCode != null && !invitationCode.isEmpty()) {
            String clientIp = com.actionow.common.core.context.UserContextHolder.getContext().getClientIp();
            invitationCodeService.useCode(invitationCode, user, clientIp, null);
        }

        // 为新用户生成专属邀请码
        if (invitationCodeService.isUserCodeEnabled()) {
            try {
                invitationCodeService.generateUserCode(user.getId());
            } catch (Exception e) {
                log.warn("为用户生成邀请码失败: userId={}, error={}", user.getId(), e.getMessage());
            }
        }

        // 异步发送欢迎邮件
        userMailHelper.sendWelcomeEmailAsync(user);

        // 生成Token（登录初始态不绑定工作空间）
        TokenResponse token = issueTokenPair(user, null, null, null, null, null, null, null);

        return LoginResponse.builder()
                .user(userService.toResponse(user))
                .token(token)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 查找用户
        User user = userService.findByCredential(request.getAccount())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 检查账号是否被锁定
        if (userService.isAccountLocked(user)) {
            throw new BusinessException(ResultCode.LOGIN_FAILED_LIMIT);
        }

        // 检查是否需要验证码
        if (user.getLoginFailCount() >= 3) {
            validateCaptcha(request.getCaptchaKey(), request.getCaptcha());
        }

        // 检查账号状态
        UserStatus status = UserStatus.fromCode(user.getStatus());
        if (status == null || !status.canLogin()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 验证密码
        if (!EncryptUtils.matchesPasswordSafe(request.getPassword(), user.getPassword())) {
            handleLoginFail(user);
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }

        // 登录成功，重置失败次数
        userService.resetLoginFailCount(user.getId());

        // 更新最后登录信息
        userService.updateLastLogin(user.getId(), clientIp);

        // 生成Token（登录初始态不绑定工作空间）
        TokenResponse token = issueTokenPair(user, null, null, clientIp, null, null, null, null);

        log.info("用户登录成功: userId={}, ip={}", user.getId(), clientIp);

        return LoginResponse.builder()
                .user(userService.toResponse(user))
                .token(token)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginByVerifyCode(VerifyCodeLoginRequest request, String clientIp) {
        // 查找用户
        User user = userService.findByCredential(request.getTarget())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 检查账号状态
        UserStatus status = UserStatus.fromCode(user.getStatus());
        if (status == null || !status.canLogin()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 验证验证码
        if (!verifyCodeService.validateAndDeleteVerifyCode(
                request.getTarget(), VerifyCodeType.LOGIN, request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }

        // 登录成功，重置失败次数
        userService.resetLoginFailCount(user.getId());

        // 更新最后登录信息
        userService.updateLastLogin(user.getId(), clientIp);

        // 生成Token（登录初始态不绑定工作空间）
        TokenResponse token = issueTokenPair(user, null, null, clientIp, null, null, null, null);

        log.info("用户验证码登录成功: userId={}, ip={}", user.getId(), clientIp);

        return LoginResponse.builder()
                .user(userService.toResponse(user))
                .token(token)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // 验证 Refresh Token
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        // 检查是否为 Refresh Token
        if (!jwtUtils.isRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "非法的Refresh Token");
        }

        // 解析 Token
        JwtClaims claims = jwtUtils.parseToken(refreshToken);
        if (claims.getSessionId() == null || claims.getSessionId().isBlank() || claims.getTokenId() == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh Token缺少会话信息");
        }

        AuthSession session = authSessionMapper.selectById(claims.getSessionId());
        if (!isSessionActive(session)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "会话已失效");
        }

        String tokenHash = hashToken(refreshToken);
        RefreshTokenRecord stored = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshTokenRecord>()
                        .eq(RefreshTokenRecord::getTokenJti, claims.getTokenId())
                        .eq(RefreshTokenRecord::getDeleted, 0)
                        .last("LIMIT 1")
        );

        if (stored == null || !Objects.equals(stored.getTokenHash(), tokenHash)) {
            revokeSessionAndFamily(session, stored, REVOKE_REASON_REFRESH_REUSE);
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh Token无效");
        }

        if (!TOKEN_STATUS_ACTIVE.equalsIgnoreCase(stored.getStatus())) {
            revokeSessionAndFamily(session, stored, REVOKE_REASON_REFRESH_REUSE);
            throw new BusinessException(ResultCode.TOKEN_INVALID, "检测到Refresh Token重放，会话已撤销");
        }

        // 检查用户是否存在
        User user = userService.getById(session.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 检查账号状态
        UserStatus userStatus = UserStatus.fromCode(user.getStatus());
        if (userStatus == null || !userStatus.canLogin()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        TokenResponse token = issueTokenPair(
                user,
                session.getWorkspaceId(),
                session.getTenantSchema(),
                session.getLastIp(),
                session.getUserAgent(),
                stored.getFamilyId(),
                stored.getTokenJti(),
                session
        );

        JwtClaims newRefreshClaims = jwtUtils.parseToken(token.getRefreshToken());
        int updated = refreshTokenMapper.markTokenUsed(
                stored.getTokenJti(),
                newRefreshClaims.getTokenId(),
                LocalDateTime.now()
        );
        if (updated != 1) {
            // 并发刷新导致的状态竞争，按重放处理并撤销整个会话族（fail-closed）。
            revokeSessionAndFamily(session, stored, REVOKE_REASON_REFRESH_REUSE);
            throw new BusinessException(ResultCode.TOKEN_INVALID, "检测到Refresh Token并发重放，会话已撤销");
        }

        log.info("Token刷新成功: userId={}", user.getId());

        return token;
    }

    @Override
    public void logout(String userId, String sessionId) {
        LocalDateTime now = LocalDateTime.now();
        if (sessionId != null && !sessionId.isBlank()) {
            // 仅撤销当前会话（DB + Redis session级黑名单）
            authSessionMapper.revokeSessionById(sessionId, now);
            revokeSessionTokensAt(sessionId);
            log.info("用户登出当前会话: userId={}, sessionId={}", userId, sessionId);
        } else {
            // sessionId不可用时回退到撤销所有会话（DB + Redis user级黑名单）
            authSessionMapper.revokeActiveSessionsByUserId(userId, now);
            revokeUserTokensAt(userId);
            redisCacheService.delete(RedisKeyConstants.USER_TOKEN + userId);
            log.info("用户登出（无sessionId，已撤销所有会话）: userId={}", userId);
        }
    }

    @Override
    public void logoutAll(String userId) {
        LocalDateTime now = LocalDateTime.now();
        authSessionMapper.revokeActiveSessionsByUserId(userId, now);
        revokeUserTokensAt(userId);
        redisCacheService.delete(RedisKeyConstants.USER_TOKEN + userId);
        log.info("用户登出全部设备: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse switchWorkspace(String userId, String workspaceId, String clientIp, String userAgent) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "workspaceId不能为空");
        }

        Result<WorkspaceMembershipInfo> membership = workspaceInternalClient.getMembership(workspaceId, userId);
        WorkspaceMembershipInfo membershipInfo = membership != null ? membership.getData() : null;

        if (membershipInfo == null || !membershipInfo.isMember()) {
            throw new BusinessException(ResultCode.NOT_WORKSPACE_MEMBER);
        }
        if (membershipInfo.getTenantSchema() == null || membershipInfo.getTenantSchema().isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "工作空间未配置schema");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 高安全策略：切换工作空间时撤销该用户所有旧token，阻断旧上下文继续访问
        authSessionMapper.revokeActiveSessionsByUserId(userId, LocalDateTime.now());
        revokeUserTokensAt(userId);

        TokenResponse token = issueTokenPair(
                user,
                workspaceId,
                membershipInfo.getTenantSchema(),
                clientIp,
                userAgent,
                null,
                null,
                null
        );

        log.info("工作空间切换成功并重签token: userId={}, workspaceId={}, schema={}",
                userId, workspaceId, membershipInfo.getTenantSchema());
        return token;
    }

    @Override
    public UserResponse getCurrentUser(String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return userService.toResponse(user);
    }

    @Override
    public CaptchaResult generateCaptcha() {
        // 生成验证码
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 50);
        String code = captcha.getCode();
        String captchaKey = UuidGenerator.generateShortId();

        // 缓存验证码
        redisCacheService.set(
                RedisKeyConstants.CAPTCHA + captchaKey,
                code.toLowerCase(),
                RedisKeyConstants.TTL_CAPTCHA,
                TimeUnit.SECONDS
        );

        // 转为Base64
        String imageBase64 = "data:image/png;base64,%s".formatted(Base64.getEncoder().encodeToString(captcha.getImageBytes()));

        return new CaptchaResult(captchaKey, imageBase64);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!EncryptUtils.matchesPasswordSafe(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT, "原密码错误");
        }

        // 更新密码
        userService.updatePassword(userId, request.getNewPassword());

        // 清除所有Token（强制重新登录）
        authSessionMapper.revokeActiveSessionsByUserId(userId, LocalDateTime.now());
        revokeUserTokensAt(userId);
        redisCacheService.delete(RedisKeyConstants.USER_TOKEN + userId);

        // 异步发送密码修改通知邮件
        userMailHelper.sendSecurityAlertAsync(user, "PASSWORD_CHANGED", "密码已修改",
                "您的账号密码已被修改。如果这不是您本人操作，请立即联系我们。",
                null, "密码修改");

        log.info("用户修改密码成功: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        // 查找用户
        User user = userService.findByCredential(request.getTarget())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 验证验证码
        if (!verifyCodeService.validateAndDeleteVerifyCode(
                request.getTarget(), VerifyCodeType.RESET_PASSWORD, request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }

        // 更新密码
        userService.updatePassword(user.getId(), request.getNewPassword());

        // 清除所有Token（强制重新登录）
        authSessionMapper.revokeActiveSessionsByUserId(user.getId(), LocalDateTime.now());
        revokeUserTokensAt(user.getId());
        redisCacheService.delete(RedisKeyConstants.USER_TOKEN + user.getId());

        // 异步发送密码重置通知邮件
        userMailHelper.sendSecurityAlertAsync(user, "PASSWORD_RESET", "密码已重置",
                "您的账号密码已通过验证码重置。如果这不是您本人操作，请立即联系我们。",
                null, "验证码重置密码");

        log.info("用户重置密码成功: userId={}", user.getId());
    }

    /**
     * 验证图形验证码
     */
    private void validateCaptcha(String captchaKey, String captcha) {
        if (captchaKey == null || captcha == null) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }

        String cachedCode = redisCacheService.get(RedisKeyConstants.CAPTCHA + captchaKey);
        if (cachedCode == null || !cachedCode.equalsIgnoreCase(captcha)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }

        // 验证成功后删除缓存
        redisCacheService.delete(RedisKeyConstants.CAPTCHA + captchaKey);
    }

    /**
     * 处理登录失败
     */
    private void handleLoginFail(User user) {
        userService.incrementLoginFailCount(user.getId());

        int failCount = user.getLoginFailCount() + 1;
        if (failCount >= MAX_LOGIN_FAIL_COUNT) {
            userService.lockAccount(user.getId(), LOCK_MINUTES);

            // 异步发送账号锁定通知邮件
            userMailHelper.sendAccountLockedAlertAsync(user, LOCK_MINUTES);
        }
    }

    private TokenResponse issueTokenPair(User user,
                                         String workspaceId,
                                         String tenantSchema,
                                         String clientIp,
                                         String userAgent,
                                         String existingFamilyId,
                                         String parentTokenJti,
                                         AuthSession existingSession) {
        AuthSession session = existingSession != null
                ? existingSession
                : createAuthSession(user.getId(), workspaceId, tenantSchema, clientIp, userAgent);

        if (existingSession != null) {
            session.setLastActiveAt(LocalDateTime.now());
            if (clientIp != null && !clientIp.isBlank()) {
                session.setLastIp(clientIp);
            }
            if (userAgent != null && !userAgent.isBlank()) {
                session.setUserAgent(userAgent);
            }
            authSessionMapper.updateById(session);
        }

        JwtClaims claims = JwtClaims.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .roles(new HashSet<>())
                .sessionId(session.getId())
                .workspaceId(workspaceId)
                .tenantSchema(tenantSchema)
                .permVersion(session.getPermVersion())
                .build();

        TokenResponse tokenPair = jwtUtils.generateTokenPair(claims);
        JwtClaims refreshClaims = jwtUtils.parseToken(tokenPair.getRefreshToken());
        persistRefreshToken(
                session.getId(),
                tokenPair.getRefreshToken(),
                refreshClaims.getTokenId(),
                refreshClaims.getExpiration(),
                existingFamilyId,
                parentTokenJti
        );

        cacheToken(user.getId(), tokenPair);
        return tokenPair;
    }

    private AuthSession createAuthSession(String userId,
                                          String workspaceId,
                                          String tenantSchema,
                                          String clientIp,
                                          String userAgent) {
        AuthSession session = new AuthSession();
        session.setUserId(userId);
        session.setWorkspaceId(workspaceId);
        session.setTenantSchema(tenantSchema);
        session.setStatus(SESSION_STATUS_ACTIVE);
        session.setPermVersion(1);
        session.setDeviceId(null);
        session.setUserAgent(userAgent);
        session.setLastIp(clientIp);
        session.setLastActiveAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpire()));
        authSessionMapper.insert(session);
        return session;
    }

    private void persistRefreshToken(String sessionId,
                                     String refreshToken,
                                     String tokenJti,
                                     Long expirationMs,
                                     String familyId,
                                     String parentTokenJti) {
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setSessionId(sessionId);
        record.setTokenJti(tokenJti);
        record.setTokenHash(hashToken(refreshToken));
        record.setFamilyId(familyId != null ? familyId : UuidGenerator.generateShortId());
        record.setParentTokenJti(parentTokenJti);
        record.setStatus(TOKEN_STATUS_ACTIVE);
        record.setIssuedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.ofEpochSecond(
                (expirationMs != null ? expirationMs : System.currentTimeMillis()) / 1000,
                0,
                java.time.ZoneOffset.UTC
        ));
        record.setReuseDetected(false);
        refreshTokenMapper.insert(record);
    }

    /**
     * 缓存Token
     */
    private void cacheToken(String userId, TokenResponse token) {
        redisCacheService.set(
                RedisKeyConstants.USER_TOKEN + userId,
                token.getAccessToken(),
                token.getExpiresIn(),
                TimeUnit.SECONDS
        );
    }

    private boolean isSessionActive(AuthSession session) {
        return session != null
                && session.getDeleted() != null
                && session.getDeleted() == 0
                && SESSION_STATUS_ACTIVE.equalsIgnoreCase(session.getStatus())
                && session.getRevokedAt() == null
                && (session.getExpiresAt() == null || session.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Token摘要计算失败");
        }
    }

    private void revokeSessionAndFamily(AuthSession session, RefreshTokenRecord storedToken, String reason) {
        LocalDateTime now = LocalDateTime.now();

        if (session != null && session.getId() != null) {
            authSessionMapper.revokeSessionById(session.getId(), now);
            revokeUserTokensAt(session.getUserId());
        }

        if (storedToken != null) {
            refreshTokenMapper.markReuseDetected(storedToken.getTokenJti(), reason, now);
            if (storedToken.getFamilyId() != null) {
                refreshTokenMapper.revokeFamily(storedToken.getFamilyId(), reason, now);
            }
        }
    }

    private void revokeUserTokensAt(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        // 记录毫秒级撤销时间戳，配合 token 的 iatMs claim 做精确撤销判断。
        redisCacheService.set(
                RedisKeyConstants.TOKEN_BLACKLIST_USER + userId,
                String.valueOf(Instant.now().toEpochMilli()),
                jwtProperties.getRefreshTokenExpire(),
                TimeUnit.SECONDS
        );
    }

    private void revokeSessionTokensAt(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        // 会话级撤销：仅使属于该session的token失效，不影响用户的其他session
        redisCacheService.set(
                RedisKeyConstants.TOKEN_BLACKLIST_SESSION + sessionId,
                String.valueOf(Instant.now().toEpochMilli()),
                jwtProperties.getRefreshTokenExpire(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public TokenResponse issueLoginToken(User user, String clientIp, String userAgent) {
        return issueTokenPair(user, null, null, clientIp, userAgent, null, null, null);
    }

}
