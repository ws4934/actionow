package com.actionow.common.mail.service;

import com.actionow.common.mail.config.MailProperties;
import com.actionow.common.mail.config.MailRuntimeConfigService;
import com.actionow.common.mail.dto.MailRequest;
import com.actionow.common.mail.dto.MailResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Cloudflare Email Sending API 的邮件服务实现
 * <p>
 * POST /accounts/{account_id}/email/sending/send
 *
 * @author Actionow
 */
public class CloudflareMailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(CloudflareMailServiceImpl.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final MailProperties mailProperties;
    private final MailRuntimeConfigService runtimeConfig;

    public CloudflareMailServiceImpl(TemplateEngine templateEngine,
                                     MailProperties mailProperties,
                                     MailRuntimeConfigService runtimeConfig) {
        this.templateEngine = templateEngine;
        this.mailProperties = mailProperties;
        this.runtimeConfig = runtimeConfig;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(resolveTimeoutMillis()))
                .build();
    }

    private long resolveTimeoutMillis() {
        if (runtimeConfig != null) {
            return runtimeConfig.getCloudflareTimeoutMillis();
        }
        return mailProperties.getCloudflare().getTimeoutMillis();
    }

    private String resolveAccountId() {
        if (runtimeConfig != null) {
            String v = runtimeConfig.getCloudflareAccountId();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return mailProperties.getCloudflare().getAccountId();
    }

    private String resolveApiToken() {
        if (runtimeConfig != null) {
            String v = runtimeConfig.getCloudflareApiToken();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return mailProperties.getCloudflare().getApiToken();
    }

    private String resolveApiBase() {
        if (runtimeConfig != null) {
            String v = runtimeConfig.getCloudflareApiBase();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return mailProperties.getCloudflare().getApiBase();
    }

    private boolean isMailEnabled() {
        return runtimeConfig != null ? runtimeConfig.isMailEnabled() : mailProperties.isEnabled();
    }

    private String resolveFromAddress(MailRequest request) {
        if (request.getFrom() != null) {
            return request.getFrom();
        }
        if (runtimeConfig != null) {
            String v = runtimeConfig.getFromAddress();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return mailProperties.getFrom();
    }

    private String resolveFromName(MailRequest request) {
        if (request.getFromName() != null) {
            return request.getFromName();
        }
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

        String accountId = resolveAccountId();
        String apiToken = resolveApiToken();
        if (accountId == null || accountId.isBlank() || apiToken == null || apiToken.isBlank()) {
            log.error("Cloudflare mail not configured: missing accountId or apiToken");
            return MailResult.failure("Cloudflare mail not configured");
        }

        try {
            return sendWithRetry(request);
        } catch (Exception e) {
            log.error("Failed to send email via Cloudflare to: {}", request.getTo(), e);
            return MailResult.failure(e.getMessage());
        }
    }

    private MailResult sendWithRetry(MailRequest request) throws Exception {
        int maxAttempts = mailProperties.getRetry().getMaxAttempts();
        long delay = mailProperties.getRetry().getDelay();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return doSend(request);
            } catch (RetryableException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Cloudflare mail attempt {} failed, retrying in {}ms: {}",
                            attempt, delay * attempt, e.getMessage());
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

    private MailResult doSend(MailRequest request) throws Exception {
        String apiBase = resolveApiBase();
        String accountId = resolveAccountId();
        String apiToken = resolveApiToken();
        long timeoutMs = resolveTimeoutMillis();

        String url = String.format("%s/accounts/%s/email/sending/send",
                trimTrailingSlash(apiBase), accountId);

        Map<String, Object> body = buildBody(request);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String respBody = response.body();

        if (status >= 500 || status == 429) {
            throw new RetryableException("HTTP " + status + ": " + respBody);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(respBody == null ? "" : respBody);
        } catch (Exception parseEx) {
            if (status >= 200 && status < 300) {
                log.info("Email sent via Cloudflare to: {} (non-JSON response)", request.getTo());
                return MailResult.success(null);
            }
            return MailResult.failure("HTTP " + status + ": " + respBody);
        }

        boolean success = root.path("success").asBoolean(false) && status >= 200 && status < 300;
        if (!success) {
            String errMsg = extractErrorMessage(root, status, respBody);
            log.error("Cloudflare mail API error to: {}, status: {}, body: {}",
                    request.getTo(), status, respBody);
            return MailResult.failure(errMsg);
        }

        String messageId = extractMessageId(root);
        log.info("Email sent successfully via Cloudflare to: {}, messageId: {}",
                request.getTo(), messageId);
        return MailResult.success(messageId);
    }

    private Map<String, Object> buildBody(MailRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        String fromAddress = resolveFromAddress(request);
        String fromName = resolveFromName(request);
        if (fromName != null && !fromName.isEmpty()) {
            Map<String, Object> fromObj = new LinkedHashMap<>();
            fromObj.put("address", fromAddress);
            fromObj.put("name", fromName);
            body.put("from", fromObj);
        } else {
            body.put("from", fromAddress);
        }

        body.put("subject", request.getSubject());
        body.put("to", request.getTo());

        if (request.getCc() != null && !request.getCc().isEmpty()) {
            body.put("cc", request.getCc());
        }
        if (request.getBcc() != null && !request.getBcc().isEmpty()) {
            body.put("bcc", request.getBcc());
        }
        if (request.getReplyTo() != null && !request.getReplyTo().isEmpty()) {
            body.put("reply_to", request.getReplyTo());
        }

        String html = resolveHtml(request);
        if (html != null) {
            body.put("html", html);
        }
        if (request.getText() != null && !request.getText().isEmpty()) {
            body.put("text", request.getText());
        } else if (html == null) {
            body.put("text", "");
        }

        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            List<Map<String, Object>> atts = new ArrayList<>(request.getAttachments().size());
            for (MailRequest.Attachment a : request.getAttachments()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", Base64.getEncoder().encodeToString(a.getContent()));
                item.put("disposition", "attachment");
                item.put("filename", a.getFilename());
                item.put("type", a.getContentType());
                atts.add(item);
            }
            body.put("attachments", atts);
        }

        return body;
    }

    private String resolveHtml(MailRequest request) {
        if (request.getTemplateName() != null) {
            Context context = new Context();
            context.setVariables(request.getTemplateVariables());
            return templateEngine.process(request.getTemplateName(), context);
        }
        return request.getHtml();
    }

    private String extractMessageId(JsonNode root) {
        JsonNode result = root.path("result");
        if (result.isObject()) {
            JsonNode delivered = result.path("delivered");
            if (delivered.isArray() && delivered.size() > 0) {
                return delivered.get(0).asText(null);
            }
            JsonNode queued = result.path("queued");
            if (queued.isArray() && queued.size() > 0) {
                return queued.get(0).asText(null);
            }
        }
        return null;
    }

    private String extractErrorMessage(JsonNode root, int status, String respBody) {
        JsonNode errors = root.path("errors");
        if (errors.isArray() && errors.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode err : errors) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(err.path("code").asText("")).append(": ").append(err.path("message").asText(""));
            }
            return sb.toString();
        }
        return "HTTP " + status + ": " + respBody;
    }

    private String trimTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    @Override
    public CompletableFuture<MailResult> sendAsync(MailRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request));
    }

    @Override
    public MailResult sendSimple(String to, String subject, String text) {
        return send(MailRequest.builder().to(to).subject(subject).text(text).build());
    }

    @Override
    public MailResult sendHtml(String to, String subject, String html) {
        return send(MailRequest.builder().to(to).subject(subject).html(html).build());
    }

    @Override
    public MailResult sendTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        return send(MailRequest.builder()
                .to(to).subject(subject).template(templateName).variables(variables).build());
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
        Map<String, Object> variables = new HashMap<>();
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

    private static class RetryableException extends Exception {
        RetryableException(String message) {
            super(message);
        }
    }
}
