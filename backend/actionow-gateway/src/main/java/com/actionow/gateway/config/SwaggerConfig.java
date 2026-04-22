package com.actionow.gateway.config;

import jakarta.annotation.PostConstruct;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 网关层 Swagger 聚合配置
 * 聚合所有微服务的 API 文档到统一入口
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = false)
public class SwaggerConfig {

    private final SwaggerUiConfigProperties swaggerUiConfigProperties;

    /**
     * 需要聚合的服务列表（服务名 -> 显示名称）
     */
    private static final List<ServiceDoc> SERVICES = List.of(
            new ServiceDoc("actionow-user", "用户服务", "/api/user"),
            new ServiceDoc("actionow-workspace", "工作空间服务", "/api/workspaces"),
            new ServiceDoc("actionow-wallet", "钱包服务", "/api/wallet"),
            new ServiceDoc("actionow-billing", "计费服务", "/api/billing"),
            new ServiceDoc("actionow-project", "项目服务", "/api/project"),
            new ServiceDoc("actionow-ai", "AI 服务", "/api/ai"),
            new ServiceDoc("actionow-task", "任务服务", "/api/tasks"),
            new ServiceDoc("actionow-collab", "协作服务", "/api/collab"),
            new ServiceDoc("actionow-system", "系统服务", "/api/system"),
            new ServiceDoc("actionow-canvas", "画布服务", "/api/canvas"),
            new ServiceDoc("actionow-agent", "Agent 服务", "/api/agent")
    );

    public SwaggerConfig(SwaggerUiConfigProperties swaggerUiConfigProperties) {
        this.swaggerUiConfigProperties = swaggerUiConfigProperties;
    }

    @PostConstruct
    public void configureSwaggerUrls() {
        Set<SwaggerUrl> urls = new HashSet<>();

        // 为每个配置的服务添加 Swagger URL
        for (ServiceDoc service : SERVICES) {
            // 构建通过网关访问各服务 API 文档的 URL
            String url = service.basePath + "/v3/api-docs";
            SwaggerUrl swaggerUrl = new SwaggerUrl(service.displayName, url, service.displayName);
            urls.add(swaggerUrl);
        }

        // 配置 Swagger UI 属性
        swaggerUiConfigProperties.setUrls(urls);
        swaggerUiConfigProperties.setDisplayRequestDuration(true);
        swaggerUiConfigProperties.setShowExtensions(true);
        swaggerUiConfigProperties.setShowCommonExtensions(true);
        swaggerUiConfigProperties.setDeepLinking(true);
        swaggerUiConfigProperties.setDefaultModelsExpandDepth(1);
        swaggerUiConfigProperties.setDefaultModelExpandDepth(1);
        swaggerUiConfigProperties.setDocExpansion("list");
        swaggerUiConfigProperties.setFilter("true");
        swaggerUiConfigProperties.setOperationsSorter("alpha");
        swaggerUiConfigProperties.setTagsSorter("alpha");
        swaggerUiConfigProperties.setTryItOutEnabled(true);
        swaggerUiConfigProperties.setPersistAuthorization(true);
    }

    private record ServiceDoc(String serviceName, String displayName, String basePath) {}
}
