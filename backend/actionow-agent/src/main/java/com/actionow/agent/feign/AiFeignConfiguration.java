package com.actionow.agent.feign;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * AI Feign 客户端独立配置
 * 使用更长的超时和更多的重试策略
 *
 * @author Actionow
 */
public class AiFeignConfiguration {

    /**
     * AI 服务专用 Retryer
     * 更激进的重试策略：初始间隔 1s，最大间隔 5s，最多重试 3 次
     */
    @Bean
    public Retryer aiRetryer() {
        return new Retryer.Default(1000, 5000, 3);
    }
}
