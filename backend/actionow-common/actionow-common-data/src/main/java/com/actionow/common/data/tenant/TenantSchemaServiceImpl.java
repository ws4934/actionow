package com.actionow.common.data.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 租户 Schema 管理服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaServiceImpl implements TenantSchemaService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void createSchema(String schemaName) {
        if (!isValidSchemaName(schemaName)) {
            throw new IllegalArgumentException("非法的Schema名称: " + schemaName);
        }

        String sql = String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName);
        jdbcTemplate.execute(sql);
        log.info("租户Schema创建成功: {}", schemaName);
    }

    @Override
    public void dropSchema(String schemaName) {
        if (!isValidSchemaName(schemaName)) {
            throw new IllegalArgumentException("非法的Schema名称: " + schemaName);
        }

        // 禁止删除 public schema
        if ("public".equalsIgnoreCase(schemaName)) {
            throw new IllegalArgumentException("不能删除public schema");
        }

        String sql = String.format("DROP SCHEMA IF EXISTS %s CASCADE", schemaName);
        jdbcTemplate.execute(sql);
        log.info("租户Schema删除成功: {}", schemaName);
    }

    @Override
    public boolean schemaExists(String schemaName) {
        if (!isValidSchemaName(schemaName)) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaName);
        return count != null && count > 0;
    }

    @Override
    public void initializeSchema(String schemaName) {
        if (!isValidSchemaName(schemaName)) {
            throw new IllegalArgumentException("非法的Schema名称: " + schemaName);
        }

        // 确保 Schema 存在
        if (!schemaExists(schemaName)) {
            createSchema(schemaName);
        }

        // 在租户 Schema 中创建必要的表
        createTenantTables(schemaName);

        log.info("租户Schema初始化完成: {}", schemaName);
    }

    /**
     * 在租户 Schema 中创建必要的表
     * 调用数据库中预定义的 create_tenant_schema 函数
     */
    private void createTenantTables(String schemaName) {
        // 调用数据库中的函数创建租户表和索引
        // 该函数在 03-init-tenant-schema.sql 中定义
        jdbcTemplate.execute(String.format("SELECT initialize_workspace('%s', '%s')", schemaName, schemaName));
        log.info("调用 initialize_workspace 函数完成租户表创建: {}", schemaName);
    }

    /**
     * 验证 Schema 名称是否合法
     */
    private boolean isValidSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return false;
        }
        // 只允许字母、数字和下划线，必须以字母或下划线开头
        return schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}
