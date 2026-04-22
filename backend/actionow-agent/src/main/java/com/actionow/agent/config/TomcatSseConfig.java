package com.actionow.agent.config;

import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tomcat SSE 配置
 * 优化 SSE 流式传输性能，禁用输出缓冲
 *
 * @author Actionow
 */
@Configuration
public class TomcatSseConfig implements WebMvcConfigurer {

    /**
     * 配置异步请求支持
     * 设置较长的超时时间以支持长时间运行的 SSE 连接
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 设置异步请求超时时间为 5 分钟
        configurer.setDefaultTimeout(300_000L);
    }

    /**
     * 自定义 Tomcat 服务器配置
     * 禁用响应缓冲以实现真正的流式传输
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            ProtocolHandler handler = connector.getProtocolHandler();
            if (handler instanceof AbstractHttp11Protocol<?> protocol) {
                // 禁用响应压缩（会导致缓冲）
                protocol.setCompression("off");
                // 设置最小响应大小为 0，确保立即发送
                protocol.setCompressionMinSize(Integer.MAX_VALUE);
                // 禁用 sendfile 以避免缓冲
                protocol.setUseSendfile(false);
            }
        });
    }
}
