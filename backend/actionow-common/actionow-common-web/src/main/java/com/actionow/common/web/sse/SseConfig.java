package com.actionow.common.web.sse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SSE 配置类
 *
 * @author Actionow
 */
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "actionow.sse")
public class SseConfig {

    /**
     * SSE 连接超时时间（毫秒），默认5分钟
     */
    private long timeout = 5 * 60 * 1000L;

    /**
     * 心跳间隔（毫秒），默认20秒
     */
    private long heartbeatInterval = 20 * 1000L;

    /**
     * 是否启用心跳
     */
    private boolean heartbeatEnabled = true;

    @Bean
    @ConditionalOnMissingBean
    public SseService sseService() {
        return new SseService(timeout);
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public void setHeartbeatEnabled(boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }
}
