package com.actionow.common.mail.service;

import com.actionow.common.mail.config.MailProperties;
import com.actionow.common.mail.config.MailRuntimeConfigService;
import com.actionow.common.mail.dto.MailRequest;
import com.actionow.common.mail.dto.MailResult;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Resend 的邮件服务实现
 *
 * @author Actionow
 */
public class ResendMailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(ResendMailServiceImpl.class);

    private final AtomicReference<Resend> resendRef = new AtomicReference<>();
    private final AtomicReference<String> currentApiKey = new AtomicReference<>();
    private final TemplateEngine templateEngine;
    private final MailProperties mailProperties;
    private final MailRuntimeConfigService runtimeConfig;

    public ResendMailServiceImpl(Resend resend, TemplateEngine templateEngine,
                                 MailProperties mailProperties,
                                 MailRuntimeConfigService runtimeConfig) {
        this.resendRef.set(resend);
        this.templateEngine = templateEngine;
        this.mailProperties = mailProperties;
        this.runtimeConfig = runtimeConfig;
        if (mailProperties.getResend() != null) {
            this.currentApiKey.set(mailProperties.getResend().getApiKey());
        }
    }

    private Resend getResendClient() {
        String desiredKey = resolveApiKey();
        String cachedKey = currentApiKey.get();
        if (desiredKey != null && !desiredKey.isBlank() && !desiredKey.equals(cachedKey)) {
            synchronized (resendRef) {
                if (!desiredKey.equals(currentApiKey.get())) {
                    log.info("Rebuilding Resend client due to API key change");
                    resendRef.set(new Resend(desiredKey));
                    currentApiKey.set(desiredKey);
                }
            }
        }
        return resendRef.get();
    }

    private String resolveApiKey() {
        if (runtimeConfig != null) {
            String v = runtimeConfig.getResendApiKey();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return mailProperties.getResend() != null ? mailProperties.getResend().getApiKey() : null;
    }

    private boolean isMailEnabled() {
        return runtimeConfig != null ? runtimeConfig.isMailEnabled() : mailProperties.isEnabled();
    }

    @Override
    public MailResult send(MailRequest request) {
        if (!isMailEnabled()) {
            log.warn("Mail service is disabled, skipping email to: {}", request.getTo());
            return MailResult.failure("Mail service is disabled");
        }

        try {
            CreateEmailResponse response = sendWithRetry(request);
            log.info("Email sent successfully via Resend to: {}, messageId: {}", request.getTo(), response.getId());
            return MailResult.success(response.getId());
        } catch (Exception e) {
            log.error("Failed to send email via Resend to: {}", request.getTo(), e);
            return MailResult.failure(e.getMessage());
        }
    }

    private CreateEmailResponse sendWithRetry(MailRequest request) throws ResendException {
        int maxAttempts = mailProperties.getRetry().getMaxAttempts();
        long delay = mailProperties.getRetry().getDelay();
        ResendException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return doSend(request);
            } catch (ResendException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Resend attempt {} failed, retrying in {}ms: {}", attempt, delay * attempt, e.getMessage());
                    try {
                        Thread.sleep(delay * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw lastException;
    }

    private CreateEmailResponse doSend(MailRequest request) throws ResendException {
        String from = formatFromAddress(request);
        String[] to = request.getTo().toArray(new String[0]);
        String subject = request.getSubject();
        String content = resolveContent(request);
        boolean isHtml = request.getHtml() != null || request.getTemplateName() != null;

        var builder = CreateEmailOptions.builder()
                .from(from)
                .to(to)
                .subject(subject);

        if (isHtml) {
            builder.html(content);
        } else {
            builder.text(content);
        }

        // Add CC if present
        if (!request.getCc().isEmpty()) {
            builder.cc(request.getCc().toArray(new String[0]));
        }

        // Add BCC if present
        if (!request.getBcc().isEmpty()) {
            builder.bcc(request.getBcc().toArray(new String[0]));
        }

        // Add Reply-To if present
        if (request.getReplyTo() != null) {
            builder.replyTo(request.getReplyTo());
        }

        return getResendClient().emails().send(builder.build());
    }

    private String formatFromAddress(MailRequest request) {
        String from = request.getFrom() != null ? request.getFrom() : resolveDefaultFrom();
        String fromName = request.getFromName() != null ? request.getFromName() : resolveDefaultFromName();

        if (fromName != null && !fromName.isEmpty()) {
            return String.format("%s <%s>", fromName, from);
        }
        return from;
    }

    private String resolveDefaultFrom() {
        if (runtimeConfig != null) {
            String v = runtimeConfig.getFromAddress();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return mailProperties.getFrom();
    }

    private String resolveDefaultFromName() {
        if (runtimeConfig != null) {
            String v = runtimeConfig.getFromDisplayName();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return mailProperties.getFromName();
    }

    @Override
    public CompletableFuture<MailResult> sendAsync(MailRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request));
    }

    @Override
    public MailResult sendSimple(String to, String subject, String text) {
        return send(MailRequest.builder()
                .to(to)
                .subject(subject)
                .text(text)
                .build());
    }

    @Override
    public MailResult sendHtml(String to, String subject, String html) {
        return send(MailRequest.builder()
                .to(to)
                .subject(subject)
                .html(html)
                .build());
    }

    @Override
    public MailResult sendTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        return send(MailRequest.builder()
                .to(to)
                .subject(subject)
                .template(templateName)
                .variables(variables)
                .build());
    }

    @Override
    public MailResult sendVerificationCode(String to, String code) {
        return sendTemplate(to, "【开拍】邮箱验证码", "mail/verification-code",
                Map.of("code", code, "expireMinutes", 10));
    }

    @Override
    public MailResult sendPasswordReset(String to, String resetLink) {
        return sendTemplate(to, "【开拍】重置密码", "mail/password-reset",
                Map.of("resetLink", resetLink, "expireMinutes", 30));
    }

    @Override
    public MailResult sendWelcome(String to, String username) {
        return sendTemplate(to, "【开拍】欢迎进组 Welcome to the Set", "mail/welcome",
                Map.of("username", username));
    }

    @Override
    public MailResult sendSecurityAlert(String to, String alertType, String alertTitle,
                                        String message, String actionTime,
                                        String ipAddress, String actionDetail) {
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("alertType", alertType);
        variables.put("alertTitle", alertTitle);
        variables.put("message", message);
        variables.put("actionTime", actionTime);
        if (ipAddress != null) {
            variables.put("ipAddress", ipAddress);
        }
        if (actionDetail != null) {
            variables.put("actionDetail", actionDetail);
        }
        return sendTemplate(to, "【开拍】安全提醒", "mail/security-alert", variables);
    }

    private String resolveContent(MailRequest request) {
        // 优先使用模板
        if (request.getTemplateName() != null) {
            Context context = new Context();
            context.setVariables(request.getTemplateVariables());
            return templateEngine.process(request.getTemplateName(), context);
        }

        // 其次使用 HTML
        if (request.getHtml() != null) {
            return request.getHtml();
        }

        // 最后使用纯文本
        return request.getText() != null ? request.getText() : "";
    }
}
