package com.actionow.collab.config;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.security.InternalAuthProperties;
import com.actionow.common.core.security.InternalAuthUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 请求拦截器
 * 将当前用户上下文中的工作空间信息传递到下游服务，并添加内部短JWT
 *
 * @author Actionow
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FeignRequestInterceptor implements RequestInterceptor {

    private final InternalAuthProperties internalAuthProperties;

    @Override
    public void apply(RequestTemplate template) {
        // 传递 Workspace ID
        String workspaceId = UserContextHolder.getWorkspaceId();
        if (workspaceId != null && !workspaceId.isBlank()) {
            template.header(CommonConstants.HEADER_WORKSPACE_ID, workspaceId);
        }

        // 传递 Tenant Schema
        String tenantSchema = UserContextHolder.getTenantSchema();
        if (tenantSchema != null && !tenantSchema.isBlank()) {
            template.header(CommonConstants.HEADER_TENANT_SCHEMA, tenantSchema);
        }

        // 传递 User ID
        String userId = UserContextHolder.getUserId();
        if (userId != null && !userId.isBlank()) {
            template.header(CommonConstants.HEADER_USER_ID, userId);
        }

        // 传递 Request ID
        String requestId = UserContextHolder.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            template.header(CommonConstants.HEADER_REQUEST_ID, requestId);
        }

        if (!internalAuthProperties.isConfigured()) {
            throw new IllegalStateException("Internal auth secret must be configured for Feign calls");
        }
        String internalToken = InternalAuthUtils.generateInternalToken(
                internalAuthProperties.getAuthSecret(),
                userId,
                workspaceId,
                tenantSchema,
                internalAuthProperties.getInternalTokenExpireSeconds()
        );
        template.header(CommonConstants.HEADER_INTERNAL_AUTH_TOKEN, internalToken);

        log.debug("Feign请求头传递: workspaceId={}, tenantSchema={}, userId={}",
                workspaceId, tenantSchema, userId);
    }
}
