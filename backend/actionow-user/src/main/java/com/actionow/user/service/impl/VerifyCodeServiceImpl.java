package com.actionow.user.service.impl;

import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.common.mail.dto.MailRequest;
import com.actionow.common.mail.service.MailService;
import com.actionow.common.redis.service.RedisCacheService;
import com.actionow.user.enums.VerifyCodeType;
import com.actionow.user.service.VerifyCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 验证码服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyCodeServiceImpl implements VerifyCodeService {

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRE_SECONDS = 300; // 5分钟
    private static final int EXPIRE_MINUTES = 5;
    private static final String VERIFY_CODE_PREFIX = RedisKeyConstants.PREFIX + "verify:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 邮箱正则表达式
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private final RedisCacheService redisCacheService;
    private final MailService mailService;

    @Override
    public int sendVerifyCode(String target, VerifyCodeType type) {
        // 生成6位数字验证码
        String code = generateCode();
        String key = buildKey(target, type);

        // 存储到Redis
        redisCacheService.set(key, code, EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 根据目标类型发送验证码
        if (isEmail(target)) {
            sendEmailVerifyCode(target, code, type);
        } else {
            // TODO: 短信发送实现
            log.info("发送短信验证码: target={}, type={}", target, type.getCode());
        }

        return EXPIRE_SECONDS;
    }

    @Override
    public boolean validateVerifyCode(String target, VerifyCodeType type, String verifyCode) {
        String key = buildKey(target, type);
        String cachedCode = redisCacheService.get(key);
        return cachedCode != null && cachedCode.equals(verifyCode);
    }

    @Override
    public boolean validateAndDeleteVerifyCode(String target, VerifyCodeType type, String verifyCode) {
        String key = buildKey(target, type);
        // 原子获取并删除，防止并发请求复用同一验证码
        String cachedCode = redisCacheService.getAndDelete(key);
        return cachedCode != null && cachedCode.equals(verifyCode);
    }

    @Override
    public void deleteVerifyCode(String target, VerifyCodeType type) {
        String key = buildKey(target, type);
        redisCacheService.delete(key);
    }

    /**
     * 构建Redis Key
     */
    private String buildKey(String target, VerifyCodeType type) {
        return VERIFY_CODE_PREFIX + type.getCode() + ":" + target;
    }

    /**
     * 生成验证码（使用SecureRandom保证不可预测）
     */
    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 判断目标是否为邮箱
     */
    private boolean isEmail(String target) {
        return target != null && EMAIL_PATTERN.matcher(target).matches();
    }

    /**
     * 异步发送邮箱验证码
     */
    private void sendEmailVerifyCode(String email, String code, VerifyCodeType type) {
        String subject = getEmailSubject(type);

        mailService.sendAsync(MailRequest.builder()
                .to(email)
                .subject(subject)
                .template("mail/verification-code")
                .variable("code", code)
                .variable("expireMinutes", EXPIRE_MINUTES)
                .build()
        ).thenAccept(result -> {
            if (result.isSuccess()) {
                log.info("邮件验证码发送成功: email={}, type={}, messageId={}", email, type.getCode(), result.getMessageId());
            } else {
                log.error("邮件验证码发送失败: email={}, type={}, error={}", email, type.getCode(), result.getErrorMessage());
            }
        }).exceptionally(ex -> {
            log.error("邮件验证码发送异常: email={}, type={}, error={}", email, type.getCode(), ex.getMessage());
            return null;
        });
    }

    /**
     * 根据验证码类型获取邮件主题
     */
    private String getEmailSubject(VerifyCodeType type) {
        return switch (type) {
            case REGISTER -> "【开拍】注册验证码";
            case LOGIN -> "【开拍】登录验证码";
            case RESET_PASSWORD -> "【开拍】重置密码验证码";
            case BIND -> "【开拍】绑定邮箱验证码";
        };
    }
}
