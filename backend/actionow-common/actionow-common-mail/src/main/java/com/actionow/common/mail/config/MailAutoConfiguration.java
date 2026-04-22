package com.actionow.common.mail.config;

import com.actionow.common.mail.service.CloudflareMailServiceImpl;
import com.actionow.common.mail.service.DynamicMailService;
import com.actionow.common.mail.service.MailService;
import com.actionow.common.mail.service.MailServiceImpl;
import com.actionow.common.mail.service.ResendMailServiceImpl;
import com.resend.Resend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

/**
 * 邮件服务自动配置
 * <p>
 * 注册 Resend / Cloudflare / SMTP 三种 provider 实现（无条件），
 * 通过 {@link DynamicMailService} 按运行时配置 {@code mail.provider} 分发。
 * <p>
 * 动态配置通过 {@link MailRuntimeConfigService} 从 t_system_config 读取，
 * 支持凭证、provider 开关的热更新。
 *
 * @author Actionow
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@EnableConfigurationProperties(MailProperties.class)
@ConditionalOnProperty(prefix = "actionow.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MailAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MailAutoConfiguration.class);

    /**
     * Mail 运行时配置（读取 t_system_config, 监听 Redis pub/sub）
     */
    @Bean
    @ConditionalOnMissingBean
    public MailRuntimeConfigService mailRuntimeConfigService(StringRedisTemplate redisTemplate,
                                                             RedisMessageListenerContainer listenerContainer,
                                                             MailProperties mailProperties) {
        log.info("Initializing MailRuntimeConfigService");
        return new MailRuntimeConfigService(redisTemplate, listenerContainer, mailProperties);
    }

    /**
     * Resend 客户端（启动时用占位符创建，真实 apiKey 通过运行时配置动态注入）
     */
    @Bean
    @ConditionalOnMissingBean(Resend.class)
    public Resend resend(MailProperties mailProperties) {
        String apiKey = mailProperties.getResend() != null ? mailProperties.getResend().getApiKey() : null;
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "re_placeholder";
        }
        log.info("Initializing Resend client (API key may be replaced at runtime)");
        return new Resend(apiKey);
    }

    /**
     * Resend 邮件服务实现
     */
    @Bean("resendMailService")
    public MailService resendMailService(Resend resend, TemplateEngine templateEngine,
                                         MailProperties mailProperties,
                                         MailRuntimeConfigService runtimeConfig) {
        return new ResendMailServiceImpl(resend, templateEngine, mailProperties, runtimeConfig);
    }

    /**
     * Cloudflare 邮件服务实现
     */
    @Bean("cloudflareMailService")
    public MailService cloudflareMailService(TemplateEngine templateEngine,
                                             MailProperties mailProperties,
                                             MailRuntimeConfigService runtimeConfig) {
        return new CloudflareMailServiceImpl(templateEngine, mailProperties, runtimeConfig);
    }

    /**
     * SMTP 邮件服务实现（仅当 JavaMailSender 存在时创建）
     */
    @Bean("smtpMailService")
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    public MailService smtpMailService(JavaMailSender mailSender, TemplateEngine templateEngine,
                                       MailProperties mailProperties,
                                       MailRuntimeConfigService runtimeConfig) {
        return new MailServiceImpl(mailSender, templateEngine, mailProperties, runtimeConfig);
    }

    /**
     * 动态分发器：按 {@code mail.provider} 运行时配置路由到具体实现
     */
    @Bean
    @Primary
    public MailService mailService(@org.springframework.beans.factory.annotation.Qualifier("resendMailService") MailService resendImpl,
                                   @org.springframework.beans.factory.annotation.Qualifier("cloudflareMailService") MailService cloudflareImpl,
                                   ObjectProvider<MailService> smtpImplProvider,
                                   MailRuntimeConfigService runtimeConfig) {
        MailService smtpImpl = smtpImplProvider.stream()
                .filter(bean -> bean.getClass() == MailServiceImpl.class)
                .findFirst()
                .orElse(null);
        log.info("Using DynamicMailService dispatcher (active provider resolved per send)");
        return new DynamicMailService(resendImpl, cloudflareImpl, smtpImpl, runtimeConfig);
    }
}
