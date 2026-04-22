package com.actionow.system.service;

import com.actionow.common.core.result.PageResult;
import com.actionow.system.dto.SystemConfigGroupedResponse;
import com.actionow.system.dto.SystemConfigRequest;
import com.actionow.system.dto.SystemConfigResponse;

import java.util.List;

/**
 * 系统配置服务接口
 *
 * @author Actionow
 */
public interface SystemConfigService {

    /**
     * 创建配置
     */
    SystemConfigResponse create(SystemConfigRequest request, String operatorId);

    /**
     * 更新配置
     */
    SystemConfigResponse update(String id, SystemConfigRequest request, String operatorId);

    /**
     * 删除配置
     */
    void delete(String id, String operatorId);

    /**
     * 获取配置详情
     */
    SystemConfigResponse getById(String id);

    /**
     * 按键获取配置值
     */
    String getConfigValue(String configKey, String scope, String scopeId);

    /**
     * 按键获取配置值（带默认值）
     */
    String getConfigValue(String configKey, String scope, String scopeId, String defaultValue);

    /**
     * 获取全局配置列表
     */
    List<SystemConfigResponse> listGlobalConfigs();

    /**
     * 按类型获取配置列表
     */
    List<SystemConfigResponse> listByType(String configType, String scope);

    /**
     * 获取工作空间配置列表
     */
    List<SystemConfigResponse> listByWorkspace(String workspaceId);

    /**
     * 分页查询配置列表
     */
    PageResult<SystemConfigResponse> listPage(Long current, Long size,
                                               String configType, String scope,
                                               String keyword, String module);

    /**
     * 按模块分组查询配置
     */
    List<SystemConfigGroupedResponse> listGroupedByModule();

    /**
     * 按键获取配置值（敏感值掩码，用于外部API）
     */
    String getConfigValueMasked(String configKey, String scope, String scopeId, String defaultValue);

    /**
     * 刷新配置缓存
     */
    void refreshCache();
}
