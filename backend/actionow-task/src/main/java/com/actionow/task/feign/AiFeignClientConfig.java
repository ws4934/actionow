package com.actionow.task.feign;

import com.actionow.task.config.TaskRuntimeConfigService;
import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * AI 服务 Feign 客户端专属配置
 * 将 read timeout 绑定到 system 模块的 runtime 配置。
 *
 * 注意：
 * - 此类故意不加 @Configuration，避免被主应用上下文扫描成全局 Feign 配置
 * - 仅通过 @FeignClient(configuration = ...) 挂载到 AiFeignClient
 */
public class AiFeignClientConfig {

    @Bean
    Request.Options aiFeignRequestOptions(TaskRuntimeConfigService runtimeConfig) {
        return new RuntimeAwareRequestOptions(runtimeConfig);
    }

    private static final class RuntimeAwareRequestOptions extends Request.Options {

        private final TaskRuntimeConfigService runtimeConfig;

        private RuntimeAwareRequestOptions(TaskRuntimeConfigService runtimeConfig) {
            super(runtimeConfig.getFeignConnectTimeoutMs(), TimeUnit.MILLISECONDS,
                    runtimeConfig.getAiFeignReadTimeoutMs(), TimeUnit.MILLISECONDS, true);
            this.runtimeConfig = runtimeConfig;
        }

        @Override
        public int readTimeoutMillis() {
            return runtimeConfig.getAiFeignReadTimeoutMs();
        }

        @Override
        public long readTimeout() {
            return runtimeConfig.getAiFeignReadTimeoutMs();
        }

        @Override
        public TimeUnit readTimeoutUnit() {
            return TimeUnit.MILLISECONDS;
        }
    }
}
