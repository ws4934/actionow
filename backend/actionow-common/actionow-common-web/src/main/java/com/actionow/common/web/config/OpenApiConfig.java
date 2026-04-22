package com.actionow.common.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 统一配置
 * 为所有微服务提供统一的 Swagger 配置，包含 JWT Bearer Token 认证
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Value("${spring.application.name:Actionow Service}")
    private String applicationName;

    @Value("${springdoc.info.version:1.0.0}")
    private String apiVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title(applicationName + " API")
                .description("""
                        Actionow 剧本与分镜创作平台 API 文档

                        ## 认证方式

                        本 API 使用 JWT Bearer Token 认证。请在请求头中添加：

                        ```
                        Authorization: Bearer <your_jwt_token>
                        ```

                        ### 获取 Token

                        1. 调用 `/api/user/auth/login` 接口获取 token
                        2. 点击右上角 "Authorize" 按钮
                        3. 输入 token（无需添加 "Bearer " 前缀）

                        ### Token 有效期

                        - Access Token: 2 小时
                        - Refresh Token: 7 天
                        """)
                .version(apiVersion)
                .contact(new Contact()
                        .name("Actionow Team")
                        .email("support@actionow.ai")
                        .url("https://actionow.ai"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://actionow.ai/license"));
    }

    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer Token 认证。登录后获取 token，在此处输入（无需添加 Bearer 前缀）");
    }
}
