package com.actionow.common.mail.config;

import com.actionow.common.redis.config.RuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 邮件模块运行时配置服务
 * <p>
 * 所有以 "mail." 为前缀的配置键均通过 t_system_config 动态管理，
 * 支持 provider 切换、凭证热更新。
 *
 * @author Actionow
 */
@Slf4j
public class MailRuntimeConfigService extends RuntimeConfigService {

    // ==================== 全局 ====================
    public static final String ENABLED                    = "mail.enabled";
    public static final String PROVIDER                   = "mail.provider";
    public static final String FROM                       = "mail.from";
    public static final String FROM_NAME                  = "mail.from_name";

    // ==================== Resend ====================
    public static final String RESEND_API_KEY             = "mail.resend.api_key";

    // ==================== Cloudflare ====================
    public static final String CLOUDFLARE_ACCOUNT_ID      = "mail.cloudflare.account_id";
    public static final String CLOUDFLARE_API_TOKEN       = "mail.cloudflare.api_token";
    public static final String CLOUDFLARE_API_BASE        = "mail.cloudflare.api_base";
    public static final String CLOUDFLARE_TIMEOUT_MILLIS  = "mail.cloudflare.timeout_millis";

    // ==================== SMTP ====================
    public static final String SMTP_HOST                  = "mail.smtp.host";
    public static final String SMTP_PORT                  = "mail.smtp.port";
    public static final String SMTP_USERNAME              = "mail.smtp.username";
    public static final String SMTP_PASSWORD              = "mail.smtp.password";
    public static final String SMTP_REGION                = "mail.smtp.region";

    private final MailProperties properties;
    private final CopyOnWriteArrayList<BiConsumer<String, String>> changeListeners = new CopyOnWriteArrayList<>();

    public MailRuntimeConfigService(StringRedisTemplate redisTemplate,
                                    RedisMessageListenerContainer listenerContainer,
                                    MailProperties properties) {
        super(redisTemplate, listenerContainer);
        this.properties = properties;
    }

    @Override
    protected String getPrefix() {
        return "mail";
    }

    @Override
    protected void registerDefaults(Map<String, String> defaults) {
        defaults.put(ENABLED,       String.valueOf(properties.isEnabled()));
        defaults.put(PROVIDER,      properties.getProvider() != null
                ? properties.getProvider().name().toLowerCase()
                : "resend");
        defaults.put(FROM,          nullToEmpty(properties.getFrom()));
        defaults.put(FROM_NAME,     nullToEmpty(properties.getFromName()));

        MailProperties.Resend resend = properties.getResend();
        defaults.put(RESEND_API_KEY, nullToEmpty(resend != null ? resend.getApiKey() : null));

        MailProperties.Cloudflare cf = properties.getCloudflare();
        defaults.put(CLOUDFLARE_ACCOUNT_ID,     nullToEmpty(cf != null ? cf.getAccountId() : null));
        defaults.put(CLOUDFLARE_API_TOKEN,      nullToEmpty(cf != null ? cf.getApiToken() : null));
        defaults.put(CLOUDFLARE_API_BASE,       cf != null && cf.getApiBase() != null
                ? cf.getApiBase() : "https://api.cloudflare.com/client/v4");
        defaults.put(CLOUDFLARE_TIMEOUT_MILLIS, String.valueOf(cf != null ? cf.getTimeoutMillis() : 10_000L));

        defaults.put(SMTP_HOST,     "");
        defaults.put(SMTP_PORT,     "587");
        defaults.put(SMTP_USERNAME, "");
        defaults.put(SMTP_PASSWORD, "");
        defaults.put(SMTP_REGION,   nullToEmpty(properties.getRegion()));
    }

    @Override
    protected void onConfigChanged(String key, String oldValue, String newValue) {
        log.info("[MailRuntimeConfig] {} changed", key);
        for (BiConsumer<String, String> listener : changeListeners) {
            try {
                listener.accept(key, newValue);
            } catch (Exception e) {
                log.warn("[MailRuntimeConfig] listener error for key={}: {}", key, e.getMessage());
            }
        }
    }

    /**
     * 注册配置变更回调（impl 侧用于热重建客户端）
     */
    public void addChangeListener(BiConsumer<String, String> listener) {
        changeListeners.add(listener);
    }

    // ==================== 便捷 Getter ====================

    public boolean isMailEnabled() {
        return getBoolean(ENABLED);
    }

    public String getProvider() {
        String p = getString(PROVIDER);
        return p == null ? "resend" : p.toLowerCase();
    }

    public String getFromAddress() {
        return getString(FROM);
    }

    public String getFromDisplayName() {
        return getString(FROM_NAME);
    }

    public String getResendApiKey() {
        return getString(RESEND_API_KEY);
    }

    public String getCloudflareAccountId() {
        return getString(CLOUDFLARE_ACCOUNT_ID);
    }

    public String getCloudflareApiToken() {
        return getString(CLOUDFLARE_API_TOKEN);
    }

    public String getCloudflareApiBase() {
        return getString(CLOUDFLARE_API_BASE);
    }

    public long getCloudflareTimeoutMillis() {
        try {
            return getLong(CLOUDFLARE_TIMEOUT_MILLIS);
        } catch (Exception e) {
            return 10_000L;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
