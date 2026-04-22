package com.actionow.user.service;

import com.actionow.common.mail.dto.MailRequest;
import com.actionow.common.mail.service.MailService;
import com.actionow.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户邮件通知助手
 * 集中管理用户相关的邮件发送逻辑
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMailHelper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MailService mailService;

    /**
     * 异步发送欢迎邮件
     */
    public void sendWelcomeEmailAsync(User user) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            return;
        }
        String nickname = user.getNickname() != null ? user.getNickname() : user.getUsername();
        mailService.sendAsync(
                MailRequest.builder()
                        .to(user.getEmail())
                        .subject("【开拍】欢迎进组 Welcome to the Set")
                        .template("mail/welcome")
                        .variable("username", nickname)
                        .build()
        ).exceptionally(ex -> {
            log.warn("发送欢迎邮件失败: userId={}, email={}, error={}", user.getId(), user.getEmail(), ex.getMessage());
            return null;
        });
    }

    /**
     * 异步发送安全提醒邮件
     */
    public void sendSecurityAlertAsync(User user, String alertType, String alertTitle,
                                       String message, String ipAddress, String actionDetail) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            return;
        }
        String actionTime = LocalDateTime.now().format(TIME_FORMATTER);
        mailService.sendAsync(
                MailRequest.builder()
                        .to(user.getEmail())
                        .subject("【开拍】安全提醒")
                        .template("mail/security-alert")
                        .variable("alertType", alertType)
                        .variable("alertTitle", alertTitle)
                        .variable("message", message)
                        .variable("actionTime", actionTime)
                        .variable("ipAddress", ipAddress)
                        .variable("actionDetail", actionDetail)
                        .build()
        ).exceptionally(ex -> {
            log.warn("发送安全提醒邮件失败: userId={}, alertType={}, error={}", user.getId(), alertType, ex.getMessage());
            return null;
        });
    }

    /**
     * 异步发送账号锁定通知邮件
     */
    public void sendAccountLockedAlertAsync(User user, int lockMinutes) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            return;
        }
        String actionTime = LocalDateTime.now().format(TIME_FORMATTER);
        Map<String, Object> variables = new HashMap<>();
        variables.put("alertType", "ACCOUNT_LOCKED");
        variables.put("alertTitle", "账号已锁定");
        variables.put("message", "您的账号因连续多次登录失败已被临时锁定。");
        variables.put("actionTime", actionTime);
        variables.put("lockMinutes", lockMinutes);

        mailService.sendAsync(
                MailRequest.builder()
                        .to(user.getEmail())
                        .subject("【开拍】安全提醒 - 账号已锁定")
                        .template("mail/security-alert")
                        .variables(variables)
                        .build()
        ).exceptionally(ex -> {
            log.warn("发送账号锁定邮件失败: userId={}, error={}", user.getId(), ex.getMessage());
            return null;
        });
    }
}
