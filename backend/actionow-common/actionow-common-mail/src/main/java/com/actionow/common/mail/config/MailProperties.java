package com.actionow.common.mail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件服务配置属性
 *
 * @author Actionow
 */
@Data
@ConfigurationProperties(prefix = "actionow.mail")
public class MailProperties {

    /**
     * 是否启用邮件服务
     */
    private boolean enabled = true;

    /**
     * 邮件服务提供商: resend / smtp / cloudflare
     * 默认使用 resend
     */
    private Provider provider = Provider.RESEND;

    /**
     * 默认发件人地址
     */
    private String from;

    /**
     * 默认发件人名称
     */
    private String fromName = "开拍 ActioNow";

    /**
     * AWS SES 区域 (仅 smtp 模式使用)
     */
    private String region = "us-east-1";

    /**
     * 是否启用模板缓存
     */
    private boolean templateCacheEnabled = true;

    /**
     * 重试配置
     */
    private Retry retry = new Retry();

    /**
     * Resend 配置
     */
    private Resend resend = new Resend();

    /**
     * Cloudflare 配置
     */
    private Cloudflare cloudflare = new Cloudflare();

    /**
     * 邮件服务提供商枚举
     */
    public enum Provider {
        /**
         * Resend 邮件服务 (默认)
         */
        RESEND,
        /**
         * SMTP 邮件服务 (AWS SES 等)
         */
        SMTP,
        /**
         * Cloudflare Email Sending API
         */
        CLOUDFLARE
    }

    @Data
    public static class Retry {
        /**
         * 最大重试次数
         */
        private int maxAttempts = 3;

        /**
         * 重试间隔（毫秒）
         */
        private long delay = 1000;
    }

    @Data
    public static class Resend {
        /**
         * Resend API Key
         */
        private String apiKey;
    }

    @Data
    public static class Cloudflare {
        /**
         * Cloudflare Account ID
         */
        private String accountId;

        /**
         * Cloudflare API Token (Bearer)
         */
        private String apiToken;

        /**
         * API base URL
         */
        private String apiBase = "https://api.cloudflare.com/client/v4";

        /**
         * HTTP timeout (毫秒)
         */
        private long timeoutMillis = 10_000L;
    }
}
