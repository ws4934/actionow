package com.actionow.common.mail.service;

import com.actionow.common.mail.config.MailProperties;
import com.actionow.common.mail.config.MailRuntimeConfigService;
import com.actionow.common.mail.dto.MailRequest;
import com.actionow.common.mail.dto.MailResult;
import com.actionow.common.mail.exception.MailException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件服务实现（基于 AWS SES SMTP）
 *
 * @author Actionow
 */
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties mailProperties;
    private final MailRuntimeConfigService runtimeConfig;

    public MailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine,
                           MailProperties mailProperties,
                           MailRuntimeConfigService runtimeConfig) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailProperties = mailProperties;
        this.runtimeConfig = runtimeConfig;
    }

    private boolean isMailEnabled() {
        return runtimeConfig != null ? runtimeConfig.isMailEnabled() : mailProperties.isEnabled();
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
    public MailResult send(MailRequest request) {
        if (!isMailEnabled()) {
            log.warn("Mail service is disabled, skipping email to: {}", request.getTo());
            return MailResult.failure("Mail service is disabled");
        }

        try {
            MimeMessage message = createMimeMessage(request);
            mailSender.send(message);

            String messageId = message.getMessageID();
            log.info("Email sent successfully to: {}, messageId: {}", request.getTo(), messageId);
            return MailResult.success(messageId);

        } catch (MailSendException e) {
            log.error("Failed to send email to: {}", request.getTo(), e);
            return handleMailException(e, request);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", request.getTo(), e);
            return MailResult.failure(e.getMessage());
        }
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

    private MimeMessage createMimeMessage(MailRequest request) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        boolean hasAttachments = !request.getAttachments().isEmpty();
        MimeMessageHelper helper = new MimeMessageHelper(message, hasAttachments, "UTF-8");

        // 设置发件人
        String from = request.getFrom() != null ? request.getFrom() : resolveDefaultFrom();
        String fromName = request.getFromName() != null ? request.getFromName() : resolveDefaultFromName();
        helper.setFrom(new InternetAddress(from, fromName, "UTF-8"));

        // 设置收件人
        helper.setTo(request.getTo().toArray(new String[0]));

        // 设置抄送
        if (!request.getCc().isEmpty()) {
            helper.setCc(request.getCc().toArray(new String[0]));
        }

        // 设置密送
        if (!request.getBcc().isEmpty()) {
            helper.setBcc(request.getBcc().toArray(new String[0]));
        }

        // 设置回复地址
        if (request.getReplyTo() != null) {
            helper.setReplyTo(request.getReplyTo());
        }

        // 设置主题
        helper.setSubject(request.getSubject());

        // 设置内容
        String content = resolveContent(request);
        boolean isHtml = request.getHtml() != null || request.getTemplateName() != null;
        helper.setText(content, isHtml);

        // 添加附件
        for (MailRequest.Attachment attachment : request.getAttachments()) {
            helper.addAttachment(attachment.getFilename(),
                    new ByteArrayResource(attachment.getContent()),
                    attachment.getContentType());
        }

        return message;
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

    private MailResult handleMailException(MailSendException e, MailRequest request) {
        int retryCount = 0;
        int maxAttempts = mailProperties.getRetry().getMaxAttempts();
        long delay = mailProperties.getRetry().getDelay();

        while (retryCount < maxAttempts - 1) {
            retryCount++;
            log.warn("Retrying to send email to: {}, attempt: {}", request.getTo(), retryCount + 1);

            try {
                Thread.sleep(delay * retryCount);
                MimeMessage message = createMimeMessage(request);
                mailSender.send(message);

                String messageId = message.getMessageID();
                log.info("Email sent successfully on retry to: {}, messageId: {}", request.getTo(), messageId);
                return MailResult.success(messageId);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return MailResult.failure("Interrupted while retrying");
            } catch (Exception retryException) {
                log.error("Retry {} failed for email to: {}", retryCount + 1, request.getTo(), retryException);
            }
        }

        return MailResult.failure("Failed after " + maxAttempts + " attempts: " + e.getMessage());
    }
}
