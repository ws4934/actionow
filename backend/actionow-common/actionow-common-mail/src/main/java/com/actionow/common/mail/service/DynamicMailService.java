package com.actionow.common.mail.service;

import com.actionow.common.mail.config.MailRuntimeConfigService;
import com.actionow.common.mail.dto.MailRequest;
import com.actionow.common.mail.dto.MailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 动态邮件服务分发器
 * <p>
 * 按 {@code mail.provider} 运行时配置将调用路由至具体 provider 实现，
 * 支持 provider 热切换，无需重启服务。
 *
 * @author Actionow
 */
public class DynamicMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(DynamicMailService.class);

    private final MailService resendImpl;
    private final MailService cloudflareImpl;
    private final MailService smtpImpl;
    private final MailRuntimeConfigService runtimeConfig;

    public DynamicMailService(MailService resendImpl,
                              MailService cloudflareImpl,
                              MailService smtpImpl,
                              MailRuntimeConfigService runtimeConfig) {
        this.resendImpl = resendImpl;
        this.cloudflareImpl = cloudflareImpl;
        this.smtpImpl = smtpImpl;
        this.runtimeConfig = runtimeConfig;
    }

    private MailService resolveDelegate() {
        String provider = runtimeConfig.getProvider();
        MailService target = switch (provider) {
            case "cloudflare" -> cloudflareImpl;
            case "smtp" -> smtpImpl;
            case "resend" -> resendImpl;
            default -> {
                log.warn("Unknown mail provider '{}', falling back to resend", provider);
                yield resendImpl;
            }
        };
        if (target == null) {
            throw new IllegalStateException(
                    "Mail provider '" + provider + "' selected but underlying bean is not configured");
        }
        return target;
    }

    @Override
    public MailResult send(MailRequest request) {
        return resolveDelegate().send(request);
    }

    @Override
    public CompletableFuture<MailResult> sendAsync(MailRequest request) {
        return resolveDelegate().sendAsync(request);
    }

    @Override
    public MailResult sendSimple(String to, String subject, String text) {
        return resolveDelegate().sendSimple(to, subject, text);
    }

    @Override
    public MailResult sendHtml(String to, String subject, String html) {
        return resolveDelegate().sendHtml(to, subject, html);
    }

    @Override
    public MailResult sendTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        return resolveDelegate().sendTemplate(to, subject, templateName, variables);
    }

    @Override
    public MailResult sendVerificationCode(String to, String code) {
        return resolveDelegate().sendVerificationCode(to, code);
    }

    @Override
    public MailResult sendPasswordReset(String to, String resetLink) {
        return resolveDelegate().sendPasswordReset(to, resetLink);
    }

    @Override
    public MailResult sendWelcome(String to, String username) {
        return resolveDelegate().sendWelcome(to, username);
    }

    @Override
    public MailResult sendSecurityAlert(String to, String alertType, String alertTitle,
                                        String message, String actionTime,
                                        String ipAddress, String actionDetail) {
        return resolveDelegate().sendSecurityAlert(to, alertType, alertTitle, message,
                actionTime, ipAddress, actionDetail);
    }
}
