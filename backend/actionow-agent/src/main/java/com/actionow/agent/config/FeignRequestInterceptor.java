package com.actionow.agent.config;

import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContext;
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
 * 支持两种上下文来源：
 * 1. ThreadLocal (UserContextHolder) - 同步调用场景
 * 2. SessionContextHolder - ADK 工具异步调用场景（跨线程）
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
        // 首先尝试从 ThreadLocal 获取上下文
        String workspaceId = UserContextHolder.getWorkspaceId();
        String tenantSchema = UserContextHolder.getTenantSchema();
        String userId = UserContextHolder.getUserId();
        String requestId = UserContextHolder.getRequestId();

        // 如果 tenantSchema 为空，尝试从 SessionContextHolder 获取（跨线程场景）
        if (tenantSchema == null || tenantSchema.isBlank()) {
            UserContext sessionContext = SessionContextHolder.getCurrentUserContext();
            if (sessionContext != null) {
                if (workspaceId == null || workspaceId.isBlank()) {
                    workspaceId = sessionContext.getWorkspaceId();
                }
                tenantSchema = sessionContext.getTenantSchema();
                if (userId == null || userId.isBlank()) {
                    userId = sessionContext.getUserId();
                }
                if (requestId == null || requestId.isBlank()) {
                    requestId = sessionContext.getRequestId();
                }
                log.debug("Using context from SessionContextHolder: workspaceId={}, tenantSchema={}",
                        workspaceId, tenantSchema);
            }
        }

        // 传递 Workspace ID
        if (workspaceId != null && !workspaceId.isBlank()) {
            template.header(CommonConstants.HEADER_WORKSPACE_ID, workspaceId);
        }

        // 传递 Tenant Schema
        if (tenantSchema != null && !tenantSchema.isBlank()) {
            template.header(CommonConstants.HEADER_TENANT_SCHEMA, tenantSchema);
        }

        // 传递 User ID
        if (userId != null && !userId.isBlank()) {
            template.header(CommonConstants.HEADER_USER_ID, userId);
        }

        // 传递 Request ID
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
