package com.actionow.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.util.EncryptUtils;
import com.actionow.user.dto.request.BindTargetRequest;
import com.actionow.user.dto.request.UpdateProfileRequest;
import com.actionow.user.dto.response.UserResponse;
import com.actionow.user.entity.User;
import com.actionow.user.enums.UserStatus;
import com.actionow.user.enums.VerifyCodeType;
import com.actionow.user.mapper.UserMapper;
import com.actionow.user.mapper.UserOauthMapper;
import com.actionow.user.service.UserService;
import com.actionow.user.service.VerifyCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 用户服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final UserOauthMapper userOauthMapper;
    private final VerifyCodeService verifyCodeService;

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(baseMapper.selectByUsername(username));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(baseMapper.selectByEmail(email));
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return Optional.ofNullable(baseMapper.selectByPhone(phone));
    }

    @Override
    public Optional<User> findByCredential(String credential) {
        // 根据格式判断是邮箱、手机号还是用户名
        Optional<User> result;
        if (EMAIL_PATTERN.matcher(credential).matches()) {
            result = findByEmail(credential);
        } else if (PHONE_PATTERN.matcher(credential).matches()) {
            result = findByPhone(credential);
        } else {
            result = findByUsername(credential);
        }
        return result;
    }

    @Override
    public boolean existsByUsername(String username) {
        return baseMapper.countByUsername(username) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return baseMapper.countByEmail(email) > 0;
    }

    @Override
    public boolean existsByPhone(String phone) {
        return baseMapper.countByPhone(phone) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String username, String email, String password, String nickname) {
        return createUser(username, email, null, password, nickname);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String username, String email, String phone, String password, String nickname) {
        return createUser(username, email, phone, password, nickname, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String username, String email, String phone, String password, String nickname,
                           String invitedBy, String invitationCodeUsed) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(EncryptUtils.encryptPassword(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setStatus(UserStatus.ACTIVE.getCode());
        user.setEmailVerified(email != null);
        user.setPhoneVerified(phone != null);
        user.setLoginFailCount(0);
        user.setInvitedBy(invitedBy);
        user.setInvitationCodeUsed(invitationCodeUsed);

        save(user);
        log.info("用户注册成功: userId={}, username={}, invitedBy={}", user.getId(), username, invitedBy);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLastLogin(String userId, String ip) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementLoginFailCount(String userId) {
        baseMapper.incrementLoginFailCount(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetLoginFailCount(String userId) {
        User user = new User();
        user.setId(userId);
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockAccount(String userId, int minutes) {
        User user = new User();
        user.setId(userId);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
        updateById(user);
        log.warn("账号已锁定: userId={}, minutes={}", userId, minutes);
    }

    @Override
    public boolean isAccountLocked(User user) {
        if (user.getLockedUntil() == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(user.getLockedUntil());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(String userId, String newPassword) {
        User user = new User();
        user.setId(userId);
        user.setPassword(EncryptUtils.encryptPassword(newPassword));
        updateById(user);
        log.info("用户密码已更新: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(String userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        boolean updated = false;

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
            updated = true;
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatar(request.getAvatarUrl());
            updated = true;
        }

        if (request.getExtraInfo() != null) {
            // 合并extraInfo
            Map<String, Object> extraInfo = user.getExtraInfo();
            if (extraInfo == null) {
                extraInfo = new HashMap<>();
            }
            extraInfo.putAll(request.getExtraInfo());
            user.setExtraInfo(extraInfo);
            updated = true;
        }

        if (updated) {
            updateById(user);
            log.info("用户资料已更新: userId={}", userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTarget(String userId, BindTargetRequest request) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证验证码
        if (!verifyCodeService.validateAndDeleteVerifyCode(
                request.getTarget(), VerifyCodeType.BIND, request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }

        if ("email".equals(request.getType())) {
            // 检查邮箱是否已被使用
            if (existsByEmail(request.getTarget())) {
                throw new BusinessException(ResultCode.EMAIL_EXISTS);
            }
            user.setEmail(request.getTarget());
            user.setEmailVerified(true);
        } else if ("phone".equals(request.getType())) {
            // 检查手机号是否已被使用
            if (existsByPhone(request.getTarget())) {
                throw new BusinessException(ResultCode.PHONE_EXISTS);
            }
            user.setPhone(request.getTarget());
            user.setPhoneVerified(true);
        } else {
            throw new BusinessException(ResultCode.PARAM_INVALID, "无效的绑定类型");
        }

        updateById(user);
        log.info("用户绑定成功: userId={}, type={}, target={}", userId, request.getType(), request.getTarget());
    }

    @Override
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        // 获取OAuth绑定列表
        var oauthList = userOauthMapper.selectByUserId(user.getId());
        var providers = oauthList.stream()
                .map(oauth -> oauth.getProvider())
                .toList();

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(maskEmail(user.getEmail()))
                .phone(maskPhone(user.getPhone()))
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .createdAt(user.getCreatedAt())
                .oauthProviders(providers)
                .systemRole(baseMapper.selectWorkspaceMemberRole(CommonConstants.SYSTEM_WORKSPACE_ID, user.getId()))
                .build();
    }

    /**
     * 邮箱脱敏
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return email;
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
