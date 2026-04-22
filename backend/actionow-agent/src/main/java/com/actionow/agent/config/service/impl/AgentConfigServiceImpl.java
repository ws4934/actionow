package com.actionow.agent.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.config.cache.AgentConfigCacheService;
import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.config.constant.AgentSkillLoadMode;
import com.actionow.agent.config.dto.AgentConfigRequest;
import com.actionow.agent.config.dto.AgentConfigResponse;
import com.actionow.agent.config.dto.AgentConfigVersionResponse;
import com.actionow.agent.config.entity.AgentConfigEntity;
import com.actionow.agent.config.entity.AgentConfigVersion;
import com.actionow.agent.config.mapper.AgentConfigMapper;
import com.actionow.agent.config.mapper.AgentConfigVersionMapper;
import com.actionow.agent.config.service.AgentConfigService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 配置服务实现
 *
 * ## 热更新机制
 * - 配置更新后自动增加版本号并清除缓存
 * - 通过 Redis Pub/Sub 通知其他服务实例
 * - triggerHotReload() 方法用于手动触发热更新
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigServiceImpl implements AgentConfigService {

    private static final String CHANNEL_AGENT_RELOAD = "agent:config:reload";

    private final AgentConfigMapper mapper;
    private final AgentConfigVersionMapper versionMapper;
    private final AgentConfigCacheService cacheService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentConfigResponse create(AgentConfigRequest request) {
        // 检查是否已存在
        AgentConfigEntity existing = mapper.selectByAgentType(request.getAgentType());
        if (existing != null) {
            throw new BusinessException("Agent 配置已存在: " + request.getAgentType());
        }

        AgentConfigEntity config = new AgentConfigEntity();
        mapRequestToEntity(request, config);
        config.setCurrentVersion(1);

        try {
            mapper.insert(config);
        } catch (DataIntegrityViolationException e) {
            translateFkViolation(e);
            throw e;
        }

        // 创建版本记录
        createVersionRecord(config, request.getChangeSummary());

        log.info("创建 Agent 配置: id={}, agentType={}", config.getId(), config.getAgentType());

        // 刷新缓存并广播热更新
        cacheService.evict(config.getId(), config.getAgentType());
        publishReloadEvent(config.getAgentType());

        return AgentConfigResponse.fromEntity(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentConfigResponse update(String id, AgentConfigRequest request) {
        AgentConfigEntity config = mapper.selectById(id);
        if (config == null || config.getDeleted() != 0) {
            throw new BusinessException("Agent 配置不存在: " + id);
        }

        // 检查系统配置的 agentType 不能修改
        if (Boolean.TRUE.equals(config.getIsSystem()) &&
                request.getAgentType() != null &&
                !request.getAgentType().equals(config.getAgentType())) {
            throw new BusinessException("系统配置的 Agent 类型不能修改");
        }

        String oldAgentType = config.getAgentType();
        mapRequestToEntity(request, config);

        // 增加版本号
        config.setCurrentVersion(config.getCurrentVersion() + 1);

        try {
            mapper.updateById(config);
        } catch (DataIntegrityViolationException e) {
            translateFkViolation(e);
            throw e;
        }

        // 创建版本记录
        createVersionRecord(config, request.getChangeSummary());

        log.info("更新 Agent 配置: id={}, version={}", id, config.getCurrentVersion());

        // 刷新缓存并广播热更新
        cacheService.evict(id, oldAgentType);
        if (!oldAgentType.equals(config.getAgentType())) {
            cacheService.evict(id, config.getAgentType());
        }
        publishReloadEvent(config.getAgentType());

        return AgentConfigResponse.fromEntity(config);
    }

    @Override
    public Optional<AgentConfigResponse> findById(String id) {
        return cacheService.get(id).map(AgentConfigResponse::fromEntity);
    }

    @Override
    public AgentConfigResponse getById(String id) {
        return findById(id).orElseThrow(() -> new BusinessException("Agent 配置不存在: " + id));
    }

    @Override
    public AgentConfigEntity getEntityById(String id) {
        return cacheService.get(id).orElseThrow(() -> new BusinessException("Agent 配置不存在: " + id));
    }

    @Override
    public Optional<AgentConfigResponse> findByAgentType(String agentType) {
        return cacheService.getByType(agentType).map(AgentConfigResponse::fromEntity);
    }

    @Override
    public AgentConfigResponse getByAgentType(String agentType) {
        return findByAgentType(agentType)
                .orElseThrow(() -> new BusinessException("Agent 配置不存在: " + agentType));
    }

    @Override
    public AgentConfigEntity getEntityByAgentType(String agentType) {
        return cacheService.getByType(agentType)
                .orElseThrow(() -> new BusinessException("Agent 配置不存在: " + agentType));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AgentConfigEntity config = mapper.selectById(id);
        if (config == null || config.getDeleted() != 0) {
            throw new BusinessException("Agent 配置不存在: " + id);
        }

        if (Boolean.TRUE.equals(config.getIsSystem())) {
            throw new BusinessException("系统配置不能删除");
        }

        mapper.deleteById(id);

        log.info("删除 Agent 配置: id={}", id);

        // 刷新缓存
        cacheService.evict(id, config.getAgentType());
    }

    @Override
    public List<AgentConfigResponse> findAllEnabled() {
        return cacheService.getAllEnabled();
    }

    @Override
    public PageResult<AgentConfigResponse> findPage(Long current, Long size, String agentType, Boolean enabled, String llmProviderId) {
        Page<AgentConfigEntity> page = new Page<>(current, size);
        IPage<AgentConfigEntity> resultPage = mapper.selectPage(page, agentType, enabled, llmProviderId);

        List<AgentConfigResponse> records = resultPage.getRecords().stream()
                .map(AgentConfigResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(current, size, resultPage.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(String id, Boolean enabled) {
        AgentConfigEntity config = mapper.selectById(id);
        if (config == null || config.getDeleted() != 0) {
            throw new BusinessException("Agent 配置不存在: " + id);
        }

        config.setEnabled(enabled);
        mapper.updateById(config);

        log.info("切换 Agent 配置启用状态: id={}, enabled={}", id, enabled);

        // 刷新缓存
        cacheService.evict(id, config.getAgentType());
    }

    @Override
    public List<AgentConfigVersionResponse> getVersionHistory(String id) {
        AgentConfigEntity config = mapper.selectById(id);
        if (config == null || config.getDeleted() != 0) {
            throw new BusinessException("Agent 配置不存在: " + id);
        }

        return versionMapper.selectByConfigId(id).stream()
                .map(AgentConfigVersionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentConfigResponse rollback(String id, Integer versionNumber) {
        AgentConfigEntity config = mapper.selectById(id);
        if (config == null || config.getDeleted() != 0) {
            throw new BusinessException("Agent 配置不存在: " + id);
        }

        AgentConfigVersion version = versionMapper.selectByConfigIdAndVersion(id, versionNumber);
        if (version == null) {
            throw new BusinessException("版本不存在: " + versionNumber);
        }

        // 恢复版本数据
        config.setPromptContent(version.getPromptContent());
        config.setIncludes(version.getIncludes());
        config.setLlmProviderId(version.getLlmProviderId());
        config.setDefaultSkillNames(version.getDefaultSkillNames());
        config.setAllowedSkillNames(version.getAllowedSkillNames());
        config.setSkillLoadMode(version.getSkillLoadMode());
        config.setExecutionMode(version.getExecutionMode());
        config.setIsCoordinator(version.getIsCoordinator());
        config.setSubAgentTypes(version.getSubAgentTypes());
        config.setStandaloneEnabled(version.getStandaloneEnabled());
        config.setCurrentVersion(config.getCurrentVersion() + 1);

        mapper.updateById(config);

        // 创建新版本记录
        createVersionRecord(config, "Rollback to version " + versionNumber);

        log.info("回滚 Agent 配置: id={}, fromVersion={}, toVersion={}",
                id, versionNumber, config.getCurrentVersion());

        // 刷新缓存
        cacheService.evict(id, config.getAgentType());

        return AgentConfigResponse.fromEntity(config);
    }

    @Override
    public boolean hasHotUpdates() {
        return cacheService.hasUpdates();
    }

    @Override
    public void refreshCache() {
        cacheService.refreshAll();
        cacheService.syncVersion();
        log.info("强制刷新所有 Agent 配置缓存");
    }

    @Override
    public void triggerHotReload(String agentType) {
        // 清除相关缓存（refreshAll 内部已 incrementVersion，无需额外调用）
        if (agentType != null && !agentType.isBlank()) {
            cacheService.refreshByType(agentType);
        }
        cacheService.refreshAll();

        // 发布 Redis 消息通知所有实例
        publishReloadEvent(agentType);

        log.info("触发热更新: agentType={}, 已通知所有服务实例", agentType);
    }

    @Override
    public void syncCacheVersion() {
        cacheService.syncVersion();
    }

    @Override
    public long getCurrentRemoteVersion() {
        return cacheService.getCurrentRemoteVersion();
    }

    @Override
    public String getResolvedPrompt(String agentType) {
        // 使用 BaseMapper.selectOne + LambdaQueryWrapper 以正确处理 JSONB 字段的 TypeHandler
        // 注意: @Select 注解不会应用 @TableField 上的 TypeHandler，导致 includes 字段为 null
        AgentConfigEntity config = selectByAgentTypeWithTypeHandler(agentType);
        if (config == null) {
            throw new BusinessException("Agent 配置不存在: " + agentType);
        }
        return resolvePromptWithIncludes(config, new HashSet<>(), new HashMap<>());
    }

    /**
     * 使用 BaseMapper.selectOne + LambdaQueryWrapper 查询
     * 这样可以正确应用 @TableField(typeHandler = JacksonTypeHandler.class) 来反序列化 JSONB 字段
     */
    private AgentConfigEntity selectByAgentTypeWithTypeHandler(String agentType) {
        return mapper.selectOne(new LambdaQueryWrapper<AgentConfigEntity>()
                .eq(AgentConfigEntity::getAgentType, agentType)
                .eq(AgentConfigEntity::getDeleted, 0));
    }

    /**
     * 递归解析 includes，构建完整提示词
     * 1. 先收集所有 include 的内容到 Map
     * 2. 然后替换 prompt 中的 {%INCLUDE_NAME%} 标记
     * 注意: 使用 {% %} 语法避免与 Google ADK 的 {{ }} 会话状态注入语法冲突
     */
    private String resolvePromptWithIncludes(AgentConfigEntity config, Set<String> visited, Map<String, String> includeContents) {
        if (config == null || visited.contains(config.getAgentType())) {
            return "";
        }
        visited.add(config.getAgentType());

        // 先处理 includes，收集内容
        List<String> includes = config.getIncludes();
        if (includes != null && !includes.isEmpty()) {
            for (String includeType : includes) {
                if (!includeContents.containsKey(includeType)) {
                    // 使用 selectByAgentTypeWithTypeHandler 以正确处理 JSONB 字段
                    AgentConfigEntity includeConfig = selectByAgentTypeWithTypeHandler(includeType);
                    if (includeConfig != null && includeConfig.getPromptContent() != null) {
                        // 递归解析 include 的内容（支持嵌套 includes）
                        String includeContent = resolvePromptWithIncludes(includeConfig, new HashSet<>(visited), includeContents);
                        includeContents.put(includeType, includeContent);
                    } else {
                        log.warn("Include config not found or has no content: {} (required by {})",
                                includeType, config.getAgentType());
                    }
                }
            }
        }

        // 获取当前配置的提示词
        String prompt = config.getPromptContent();
        if (prompt == null) {
            return "";
        }

        // 替换所有 {%INCLUDE_NAME%} 标记（使用 {% %} 避免与 Google ADK 的 {{ }} 语法冲突）
        for (Map.Entry<String, String> entry : includeContents.entrySet()) {
            String marker = "{%" + entry.getKey() + "%}";
            prompt = prompt.replace(marker, entry.getValue());
        }

        // 检查是否有未解析的 include 标记（可能是配置错误）
        if (prompt.contains("{%") && prompt.contains("%}")) {
            log.warn("Unresolved include markers detected in prompt for {}: check includes configuration",
                    config.getAgentType());
        }

        return prompt.trim();
    }

    /**
     * 创建版本记录
     */
    private void createVersionRecord(AgentConfigEntity config, String changeSummary) {
        AgentConfigVersion version = new AgentConfigVersion();
        version.setAgentConfigId(config.getId());
        version.setVersionNumber(config.getCurrentVersion());
        version.setPromptContent(config.getPromptContent());
        version.setIncludes(config.getIncludes());
        version.setLlmProviderId(config.getLlmProviderId());
        version.setDefaultSkillNames(config.getDefaultSkillNames());
        version.setAllowedSkillNames(config.getAllowedSkillNames());
        version.setSkillLoadMode(config.getSkillLoadMode());
        version.setExecutionMode(config.getExecutionMode());
        version.setIsCoordinator(config.getIsCoordinator());
        version.setSubAgentTypes(config.getSubAgentTypes());
        version.setStandaloneEnabled(config.getStandaloneEnabled());
        version.setChangeSummary(changeSummary != null ? changeSummary : "Update");

        versionMapper.insert(version);
    }

    /**
     * 映射请求到实体
     */
    private void mapRequestToEntity(AgentConfigRequest request, AgentConfigEntity entity) {
        if (request.getAgentType() != null) {
            entity.setAgentType(request.getAgentType().toUpperCase());
        }
        if (request.getAgentName() != null) {
            entity.setAgentName(request.getAgentName());
        }
        if (request.getLlmProviderId() != null) {
            entity.setLlmProviderId(request.getLlmProviderId());
        }
        if (request.getPromptContent() != null) {
            entity.setPromptContent(request.getPromptContent());
        }
        if (request.getIncludes() != null) {
            entity.setIncludes(request.getIncludes());
        }
        if (request.getDefaultSkillNames() != null) {
            entity.setDefaultSkillNames(request.getDefaultSkillNames());
        }
        if (request.getAllowedSkillNames() != null) {
            entity.setAllowedSkillNames(request.getAllowedSkillNames());
        }
        if (request.getSkillLoadMode() != null) {
            entity.setSkillLoadMode(AgentSkillLoadMode.fromCode(request.getSkillLoadMode()).name());
        } else if (entity.getSkillLoadMode() == null) {
            entity.setSkillLoadMode(AgentSkillLoadMode.ALL_ENABLED.name());
        }
        if (request.getExecutionMode() != null) {
            entity.setExecutionMode(AgentExecutionMode.fromCode(request.getExecutionMode()).name());
        } else if (entity.getExecutionMode() == null) {
            entity.setExecutionMode(AgentExecutionMode.BOTH.name());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        if (request.getIsSystem() != null) {
            entity.setIsSystem(request.getIsSystem());
        } else if (entity.getIsSystem() == null) {
            entity.setIsSystem(false);
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }

        // 自定义 Agent 扩展字段
        if (request.getIsCoordinator() != null) {
            entity.setIsCoordinator(request.getIsCoordinator());
        } else if (entity.getIsCoordinator() == null) {
            entity.setIsCoordinator(false);
        }
        if (request.getSubAgentTypes() != null) {
            entity.setSubAgentTypes(request.getSubAgentTypes());
        }
        if (request.getScope() != null) {
            entity.setScope(request.getScope().toUpperCase());
        } else if (entity.getScope() == null) {
            entity.setScope("SYSTEM");
        }
        if (request.getWorkspaceId() != null) {
            entity.setWorkspaceId(request.getWorkspaceId());
        }
        if (request.getStandaloneEnabled() != null) {
            entity.setStandaloneEnabled(request.getStandaloneEnabled());
        } else if (entity.getStandaloneEnabled() == null) {
            entity.setStandaloneEnabled(false);
        }
        if (request.getIconUrl() != null) {
            entity.setIconUrl(request.getIconUrl());
        }
        if (request.getTags() != null) {
            entity.setTags(request.getTags());
        }
    }

    // ==================== 自定义 Agent 支持方法实现 ====================

    @Override
    public List<AgentConfigResponse> findByScope(String scope, String workspaceId, String userId) {
        LambdaQueryWrapper<AgentConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentConfigEntity::getScope, scope)
                .eq(AgentConfigEntity::getEnabled, true)
                .eq(AgentConfigEntity::getDeleted, 0);

        if ("WORKSPACE".equals(scope) && workspaceId != null) {
            wrapper.eq(AgentConfigEntity::getWorkspaceId, workspaceId);
        }
        if ("USER".equals(scope) && userId != null) {
            wrapper.eq(AgentConfigEntity::getCreatorId, userId);
        }

        return mapper.selectList(wrapper).stream()
                .map(AgentConfigResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigResponse> findAvailableAgents(String workspaceId, String userId) {
        List<AgentConfigResponse> result = new ArrayList<>();

        // 1. 系统级 Agent
        result.addAll(findByScope("SYSTEM", null, null));

        // 2. 工作空间级 Agent
        if (workspaceId != null) {
            result.addAll(findByScope("WORKSPACE", workspaceId, null));
        }

        // 3. 用户级 Agent
        if (userId != null) {
            result.addAll(findByScope("USER", null, userId));
        }

        return result;
    }

    @Override
    public List<AgentConfigResponse> findCoordinators(String workspaceId, String userId) {
        return findAvailableAgents(workspaceId, userId).stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getIsCoordinator()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigResponse> findStandaloneAgents(String workspaceId, String userId) {
        return findAvailableAgents(workspaceId, userId).stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getStandaloneEnabled()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigEntity> findSubAgents(String coordinatorAgentType) {
        AgentConfigEntity coordinator = selectByAgentTypeWithTypeHandler(coordinatorAgentType);
        if (coordinator == null || coordinator.getSubAgentTypes() == null || coordinator.getSubAgentTypes().isEmpty()) {
            return Collections.emptyList();
        }

        List<AgentConfigEntity> subAgents = new ArrayList<>();
        for (String subAgentType : coordinator.getSubAgentTypes()) {
            AgentConfigEntity subAgent = selectByAgentTypeWithTypeHandler(subAgentType);
            if (subAgent != null && Boolean.TRUE.equals(subAgent.getEnabled())) {
                subAgents.add(subAgent);
            }
        }
        return subAgents;
    }

    @Override
    public List<AgentConfigEntity> findAllEnabledEntities() {
        return mapper.selectList(new LambdaQueryWrapper<AgentConfigEntity>()
                .eq(AgentConfigEntity::getEnabled, true)
                .eq(AgentConfigEntity::getDeleted, 0));
    }

    /**
     * 发布 Pub/Sub 热更新通知（所有实例清除 Agent 缓存）
     */
    private void publishReloadEvent(String agentType) {
        String message = (agentType != null && !agentType.isBlank()) ? agentType : "all";
        stringRedisTemplate.convertAndSend(CHANNEL_AGENT_RELOAD, message);
    }

    /**
     * 将外键约束违反转换为可读的 BusinessException
     */
    private void translateFkViolation(DataIntegrityViolationException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("llm_provider_id")) {
            throw new BusinessException("指定的 LLM Provider 不存在，请先在 AI 服务中创建对应的 Provider 配置");
        }
    }
}
