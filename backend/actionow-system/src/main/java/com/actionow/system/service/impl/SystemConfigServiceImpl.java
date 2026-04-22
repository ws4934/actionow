package com.actionow.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.ResultCode;
import com.actionow.system.constant.SystemConstants;
import com.actionow.system.dto.SystemConfigGroupedResponse;
import com.actionow.system.dto.SystemConfigRequest;
import com.actionow.system.dto.SystemConfigResponse;
import com.actionow.system.entity.SystemConfig;
import com.actionow.system.mapper.SystemConfigMapper;
import com.actionow.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper configMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 缓存 TTL（秒），由 application.yml 的 system.config.cache-ttl 注入，默认 1 小时 */
    @Value("${system.config.cache-ttl:3600}")
    private long cacheTtlSeconds;

    /** 创建 / 更新时禁止从 request 复制的字段（防止客户端注入） */
    private static final String[] BEAN_COPY_IGNORED = {
            "id", "createdBy", "createdAt", "updatedAt", "deleted", "version"
    };

    /** 敏感配置回传哨兵：前端原样回传此值时 update() 视为"未变更" */
    private static final String SENSITIVE_UNCHANGED_SENTINEL = "********";

    /** 模块标识 → 中文展示名 */
    private static final Map<String, String> MODULE_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("user", "用户管理"),
            Map.entry("agent", "智能体"),
            Map.entry("task", "任务管理"),
            Map.entry("ai", "AI 服务"),
            Map.entry("gateway", "网关"),
            Map.entry("project", "项目管理"),
            Map.entry("billing", "计费"),
            Map.entry("mq", "消息队列"),
            Map.entry("canvas", "画布"),
            Map.entry("system", "系统")
    );

    @Override
    @Transactional
    public SystemConfigResponse create(SystemConfigRequest request, String operatorId) {
        // 检查配置键是否已存在
        SystemConfig existing = configMapper.selectByKey(request.getConfigKey(),
                request.getScope() != null ? request.getScope() : SystemConstants.ConfigScope.GLOBAL,
                request.getScopeId());
        if (existing != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "配置键已存在");
        }

        SystemConfig config = new SystemConfig();
        // 显式排除敏感字段，杜绝客户端通过 DTO 注入 id / createdAt 等
        BeanUtils.copyProperties(request, config, BEAN_COPY_IGNORED);
        config.setScope(request.getScope() != null ? request.getScope() : SystemConstants.ConfigScope.GLOBAL);
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        config.setSensitive(request.getSensitive() != null ? request.getSensitive() : false);
        config.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (!StringUtils.hasText(config.getValueType())) {
            config.setValueType(SystemConstants.ValueType.STRING);
        }
        // 自动推断 module
        if (!StringUtils.hasText(config.getModule())) {
            config.setModule(inferModule(config.getConfigKey()));
        }
        config.setCreatedBy(operatorId);

        // 写入前按 valueType 校验
        validateValueType(config.getValueType(), config.getConfigValue(), config.getConfigKey());

        configMapper.insert(config);
        // 仅在启用时写缓存
        if (Boolean.TRUE.equals(config.getEnabled())) {
            updateCache(config);
        }
        publishChange(config.getConfigKey());

        log.info("创建系统配置: configKey={}, scope={}, valueType={}",
                config.getConfigKey(), config.getScope(), config.getValueType());
        return toResponse(config);
    }

    @Override
    @Transactional
    public SystemConfigResponse update(String id, SystemConfigRequest request, String operatorId) {
        SystemConfig config = configMapper.selectById(id);
        if (config == null || (config.getDeleted() != null && config.getDeleted() != 0)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置不存在");
        }

        // 配置值更新规则：
        // - request.configValue == null  → 不更新
        // - 等于哨兵 SENSITIVE_UNCHANGED_SENTINEL → 不更新（前端回传的占位符）
        // - 匹配当前掩码形式 → 不更新（前端回传展示掩码）
        // - 否则视为真实新值
        boolean valueUnchanged = SENSITIVE_UNCHANGED_SENTINEL.equals(request.getConfigValue())
                || (Boolean.TRUE.equals(config.getSensitive())
                    && config.getConfigValue() != null
                    && maskForDisplay(config.getConfigValue()).equals(request.getConfigValue()));
        if (request.getConfigValue() != null && !valueUnchanged) {
            // 写入前按 valueType 校验（优先用 request 的 valueType，否则沿用旧值）
            String valueType = request.getValueType() != null ? request.getValueType() : config.getValueType();
            validateValueType(valueType, request.getConfigValue(), config.getConfigKey());
            config.setConfigValue(request.getConfigValue());
        }
        if (request.getValueType() != null) config.setValueType(request.getValueType());
        if (request.getDescription() != null) config.setDescription(request.getDescription());
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getSensitive() != null) config.setSensitive(request.getSensitive());
        if (request.getModule() != null) config.setModule(request.getModule());
        if (request.getGroupName() != null) config.setGroupName(request.getGroupName());
        if (request.getDisplayName() != null) config.setDisplayName(request.getDisplayName());
        if (request.getSortOrder() != null) config.setSortOrder(request.getSortOrder());

        configMapper.updateById(config);

        // 启用 → 写/刷新缓存；禁用 → 直接 evict
        if (Boolean.TRUE.equals(config.getEnabled())) {
            updateCache(config);
        } else {
            evictCache(config);
        }

        // 发布配置变更通知，供各模块 RuntimeConfigService 订阅
        publishChange(config.getConfigKey());

        return toResponse(config);
    }

    @Override
    @Transactional
    public void delete(String id, String operatorId) {
        SystemConfig config = configMapper.selectById(id);
        if (config == null || (config.getDeleted() != null && config.getDeleted() != 0)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置不存在");
        }

        config.setDeleted(1);
        configMapper.updateById(config);

        evictCache(config);
        publishChange(config.getConfigKey());

        log.info("删除系统配置: configKey={}", config.getConfigKey());
    }

    @Override
    public SystemConfigResponse getById(String id) {
        SystemConfig config = configMapper.selectById(id);
        if (config == null || (config.getDeleted() != null && config.getDeleted() != 0)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置不存在");
        }
        return toResponse(config);
    }

    @Override
    public String getConfigValue(String configKey, String scope, String scopeId) {
        return getConfigValue(configKey, scope, scopeId, null);
    }

    @Override
    public String getConfigValue(String configKey, String scope, String scopeId, String defaultValue) {
        String cacheKey = buildCacheKey(configKey, scope, scopeId);
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            // 缓存只可能由"启用且未删除"的配置写入（见 updateCache / update / delete），
            // 因此命中缓存等同于"启用配置存在"，无需再次校验 enabled。
            return cachedValue;
        }

        SystemConfig config = configMapper.selectByKey(configKey, scope, scopeId);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            updateCache(config);
            return config.getConfigValue();
        }

        // 未启用或不存在 → 返回调用方 default，否则用 DB 中存的 default_value
        return defaultValue != null ? defaultValue : (config != null ? config.getDefaultValue() : null);
    }

    @Override
    public List<SystemConfigResponse> listGlobalConfigs() {
        return configMapper.selectGlobalConfigs().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SystemConfigResponse> listByType(String configType, String scope) {
        return configMapper.selectByType(configType, scope).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SystemConfigResponse> listByWorkspace(String workspaceId) {
        return configMapper.selectByWorkspace(workspaceId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<SystemConfigResponse> listPage(Long current, Long size,
                                                      String configType, String scope,
                                                      String keyword, String module) {
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        Page<SystemConfig> page = new Page<>(current, size);
        IPage<SystemConfig> result = configMapper.selectPage(page, configType, scope, keyword, module);

        if (result.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        List<SystemConfigResponse> records = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public String getConfigValueMasked(String configKey, String scope, String scopeId, String defaultValue) {
        SystemConfig config = configMapper.selectByKey(configKey, scope, scopeId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return defaultValue != null ? defaultValue : (config != null ? config.getDefaultValue() : null);
        }

        String cacheKey = buildCacheKey(configKey, scope, scopeId);
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        String value = cachedValue != null ? cachedValue : config.getConfigValue();
        if (cachedValue == null) {
            updateCache(config);
        }

        // 敏感配置返回部分掩码供辨认
        if (Boolean.TRUE.equals(config.getSensitive())) {
            return value == null ? null : maskForDisplay(value);
        }
        return value;
    }

    @Override
    public List<SystemConfigGroupedResponse> listGroupedByModule() {
        List<SystemConfig> allConfigs = configMapper.selectGlobalConfigs();

        // 按 module 分组 → 内部按 group_name 分组
        Map<String, Map<String, List<SystemConfig>>> moduleGroupMap = new LinkedHashMap<>();
        for (SystemConfig config : allConfigs) {
            String mod = config.getModule() != null ? config.getModule() : "system";
            String group = config.getGroupName() != null ? config.getGroupName() : "default";
            moduleGroupMap
                    .computeIfAbsent(mod, k -> new LinkedHashMap<>())
                    .computeIfAbsent(group, k -> new ArrayList<>())
                    .add(config);
        }

        List<SystemConfigGroupedResponse> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<SystemConfig>>> moduleEntry : moduleGroupMap.entrySet()) {
            SystemConfigGroupedResponse moduleResp = new SystemConfigGroupedResponse();
            moduleResp.setModule(moduleEntry.getKey());
            moduleResp.setModuleDisplayName(
                    MODULE_DISPLAY_NAMES.getOrDefault(moduleEntry.getKey(), moduleEntry.getKey()));

            List<SystemConfigGroupedResponse.GroupEntry> groups = new ArrayList<>();
            for (Map.Entry<String, List<SystemConfig>> groupEntry : moduleEntry.getValue().entrySet()) {
                SystemConfigGroupedResponse.GroupEntry ge = new SystemConfigGroupedResponse.GroupEntry();
                ge.setGroupName(groupEntry.getKey());
                ge.setConfigs(groupEntry.getValue().stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList()));
                groups.add(ge);
            }
            moduleResp.setGroups(groups);
            result.add(moduleResp);
        }
        return result;
    }

    @Override
    public void refreshCache() {
        // 仅刷新启用的配置；禁用项不应有缓存
        List<SystemConfig> configs = configMapper.selectGlobalConfigs().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .collect(Collectors.toList());
        for (SystemConfig config : configs) {
            updateCache(config);
        }
        log.info("刷新系统配置缓存: count={}", configs.size());
    }

    /**
     * 定时全量刷新 Redis 缓存。
     *
     * 解决两个问题：
     *   1. Redis 在 TTL 内被驱逐时，消费者侧 RuntimeConfigService 重启会静默回退到默认值
     *   2. 初次 Pub/Sub 消息丢失时，存量配置无法重新对齐
     *
     * 间隔从 application.yml 的 system.config.refresh-interval 注入（毫秒），默认 5 分钟。
     */
    @Scheduled(fixedDelayString = "${system.config.refresh-interval-ms:300000}",
               initialDelayString = "${system.config.refresh-initial-delay-ms:60000}")
    public void scheduledRefresh() {
        try {
            refreshCache();
        } catch (Exception e) {
            log.warn("定时刷新系统配置缓存失败: {}", e.getMessage(), e);
        }
    }

    private void updateCache(SystemConfig config) {
        String cacheKey = buildCacheKey(config.getConfigKey(), config.getScope(), config.getScopeId());
        redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(), cacheTtlSeconds, TimeUnit.SECONDS);
    }

    private void evictCache(SystemConfig config) {
        String cacheKey = buildCacheKey(config.getConfigKey(), config.getScope(), config.getScopeId());
        redisTemplate.delete(cacheKey);
    }

    private void publishChange(String configKey) {
        redisTemplate.convertAndSend(SystemConstants.Channel.CONFIG_CHANGED, configKey);
    }

    private String buildCacheKey(String configKey, String scope, String scopeId) {
        if (StringUtils.hasText(scopeId)) {
            return SystemConstants.CacheKey.CONFIG_PREFIX + scope + ":" + scopeId + ":" + configKey;
        }
        return SystemConstants.CacheKey.CONFIG_PREFIX + scope + ":" + configKey;
    }

    private SystemConfigResponse toResponse(SystemConfig config) {
        SystemConfigResponse response = new SystemConfigResponse();
        BeanUtils.copyProperties(config, response);
        // 敏感配置返回部分掩码（供人识别 key），前端回传时 update() 同时识别掩码和哨兵
        if (Boolean.TRUE.equals(config.getSensitive()) && config.getConfigValue() != null) {
            response.setConfigValue(maskForDisplay(config.getConfigValue()));
        }
        return response;
    }

    /**
     * 展示用部分掩码：保留首尾各 4 字节供人辨认，中间用 **** 替换。
     * 短值(<=8字符)完全掩盖。
     */
    private String maskForDisplay(String value) {
        if (value == null || value.length() <= 8) {
            return SENSITIVE_UNCHANGED_SENTINEL;
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 写入前按 valueType 校验 configValue，提前暴露管理员录入错误，
     * 避免下游消费者在 Integer.parseInt / Boolean.parseBoolean 时才抛异常。
     */
    private void validateValueType(String valueType, String value, String configKey) {
        if (value == null || valueType == null) {
            return;
        }
        try {
            switch (valueType) {
                case SystemConstants.ValueType.INTEGER -> Integer.parseInt(value);
                case SystemConstants.ValueType.LONG -> Long.parseLong(value);
                case SystemConstants.ValueType.FLOAT -> Float.parseFloat(value);
                case SystemConstants.ValueType.BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException("非法布尔值: " + value);
                    }
                }
                case SystemConstants.ValueType.JSON -> {
                    try {
                        objectMapper.readTree(value);
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("非法 JSON: " + ex.getMessage());
                    }
                }
                case SystemConstants.ValueType.STRING -> { /* no-op */ }
                default -> { /* 未知类型当作 STRING */ }
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    String.format("配置 %s 值类型不匹配: 期望 %s，实际值 %s", configKey, valueType, value));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    String.format("配置 %s %s", configKey, e.getMessage()));
        }
    }

    /**
     * 从 configKey 前缀推断所属模块
     */
    private String inferModule(String configKey) {
        if (configKey == null) return "system";
        if (configKey.startsWith("runtime.task.")) return "task";
        if (configKey.startsWith("runtime.agent.")) return "agent";
        if (configKey.startsWith("runtime.ai.") || configKey.startsWith("ai.provider.")) return "ai";
        if (configKey.startsWith("runtime.gateway.")) return "gateway";
        if (configKey.startsWith("runtime.project.")) return "project";
        if (configKey.startsWith("runtime.billing.")) return "billing";
        if (configKey.startsWith("runtime.mq.")) return "mq";
        if (configKey.startsWith("registration.") || configKey.startsWith("user.")) return "user";
        if (configKey.startsWith("billing.")) return "billing";
        return "system";
    }
}
