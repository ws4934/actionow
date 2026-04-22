package com.actionow.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.system.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统配置 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（validation）。
 *
 * @author Actionow
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    /**
     * 按配置键查询
     *
     * 选中规则：
     *   1. 优先返回 scope_id 完全匹配的行
     *   2. 否则回退到 scope_id 为 NULL 的全局兜底行
     *
     * 通过 ORDER BY scope_id DESC NULLS LAST 实现：非 NULL 总是排在 NULL 前面，
     * 配合 LIMIT 1 即可消除原实现中"两行都满足时随机选一行"的不确定性。
     */
    default SystemConfig selectByKey(String configKey, String scope, String scopeId) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey)
                .eq(SystemConfig::getScope, scope);
        if (scopeId != null) {
            wrapper.and(w -> w.eq(SystemConfig::getScopeId, scopeId)
                    .or()
                    .isNull(SystemConfig::getScopeId));
        } else {
            wrapper.isNull(SystemConfig::getScopeId);
        }
        // 非 NULL 排在前面 → 精确匹配优先于全局兜底
        wrapper.last("ORDER BY scope_id DESC NULLS LAST LIMIT 1");
        return selectOne(wrapper);
    }

    /**
     * 查询全局配置
     */
    default List<SystemConfig> selectGlobalConfigs() {
        return selectList(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getScope, "GLOBAL")
                .orderByAsc(SystemConfig::getSortOrder));
    }

    /**
     * 按类型查询配置
     */
    default List<SystemConfig> selectByType(String configType, String scope) {
        return selectList(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigType, configType)
                .eq(SystemConfig::getScope, scope)
                .orderByAsc(SystemConfig::getSortOrder));
    }

    /**
     * 查询工作空间配置
     */
    default List<SystemConfig> selectByWorkspace(String workspaceId) {
        return selectList(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getScope, "WORKSPACE")
                .eq(SystemConfig::getScopeId, workspaceId)
                .orderByAsc(SystemConfig::getSortOrder));
    }

    /**
     * 按模块查询全局配置
     */
    default List<SystemConfig> selectByModule(String module) {
        return selectList(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getModule, module)
                .eq(SystemConfig::getScope, "GLOBAL")
                .eq(SystemConfig::getEnabled, true)
                .orderByAsc(SystemConfig::getSortOrder));
    }

    /**
     * 分页查询配置（支持 module 筛选）
     *
     * 注意：管理员视角下不再过滤 enabled，否则被禁用的配置在列表中不可见，
     * 与 selectGlobalConfigs/listGroupedByModule 行为不一致，且无法在 UI 中重新启用。
     */
    default IPage<SystemConfig> selectPage(Page<SystemConfig> page,
                                            String configType, String scope,
                                            String keyword, String module) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(StringUtils.hasText(configType), SystemConfig::getConfigType, configType)
                .eq(StringUtils.hasText(scope), SystemConfig::getScope, scope)
                .eq(StringUtils.hasText(module), SystemConfig::getModule, module)
                .likeRight(StringUtils.hasText(keyword), SystemConfig::getConfigKey, keyword)
                .orderByAsc(SystemConfig::getSortOrder)
                .orderByDesc(SystemConfig::getCreatedAt);
        return selectPage(page, wrapper);
    }

    /**
     * 按配置键前缀查询全局配置
     * 供各模块 RuntimeConfigService 批量加载
     */
    default List<SystemConfig> selectByKeyPrefix(String prefix) {
        return selectList(new LambdaQueryWrapper<SystemConfig>()
                .likeRight(SystemConfig::getConfigKey, prefix)
                .eq(SystemConfig::getScope, "GLOBAL")
                .eq(SystemConfig::getEnabled, true)
                .orderByAsc(SystemConfig::getSortOrder));
    }
}
