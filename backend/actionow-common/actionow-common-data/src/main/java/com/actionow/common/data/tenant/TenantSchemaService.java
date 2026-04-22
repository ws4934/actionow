package com.actionow.common.data.tenant;

/**
 * 租户 Schema 管理服务接口
 *
 * @author Actionow
 */
public interface TenantSchemaService {

    /**
     * 创建租户 Schema
     *
     * @param schemaName Schema 名称
     */
    void createSchema(String schemaName);

    /**
     * 删除租户 Schema
     *
     * @param schemaName Schema 名称
     */
    void dropSchema(String schemaName);

    /**
     * 检查 Schema 是否存在
     *
     * @param schemaName Schema 名称
     * @return 是否存在
     */
    boolean schemaExists(String schemaName);

    /**
     * 初始化租户 Schema（创建必要的表）
     *
     * @param schemaName Schema 名称
     */
    void initializeSchema(String schemaName);
}
