package com.actionow.common.data.interceptor;

import com.actionow.common.core.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.Statement;

/**
 * 多租户 Schema 拦截器
 * 在执行 SQL 前设置 PostgreSQL 的 search_path 为当前租户的 Schema
 *
 * @author Actionow
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class TenantSchemaInterceptor implements Interceptor {

    private static final String DEFAULT_SCHEMA = "public";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Executor executor = (Executor) invocation.getTarget();
        Connection connection = executor.getTransaction().getConnection();

        String workspaceId = UserContextHolder.getWorkspaceId();
        String tenantSchema = UserContextHolder.getTenantSchema();

        if (workspaceId != null && !workspaceId.isBlank()
                && (tenantSchema == null || tenantSchema.isBlank())) {
            String message = String.format("workspaceId=%s exists but tenantSchema is missing", workspaceId);
            log.error("租户Schema解析失败（fail-closed）: {}", message);
            throw new IllegalStateException(message);
        }

        if (tenantSchema == null || tenantSchema.isBlank()) {
            tenantSchema = DEFAULT_SCHEMA; // 非工作空间请求走 public
        }
        String sanitizedSchema = sanitizeSchemaName(tenantSchema);

        // 设置 PostgreSQL search_path
        try (Statement statement = connection.createStatement()) {
            String sql = String.format("SET search_path TO %s, public", sanitizedSchema);
            statement.execute(sql);
            log.debug("设置租户Schema: {}", sanitizedSchema);
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * 清理 Schema 名称，防止 SQL 注入
     */
    private String sanitizeSchemaName(String schemaName) {
        // 只允许字母、数字和下划线
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid tenant schema name: " + schemaName);
        }
        return schemaName;
    }
}
