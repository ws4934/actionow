package com.actionow.agent.config.service;

import com.actionow.agent.config.dto.AgentConfigRequest;
import com.actionow.agent.config.dto.AgentConfigResponse;
import com.actionow.agent.config.dto.AgentConfigVersionResponse;
import com.actionow.agent.config.entity.AgentConfigEntity;
import com.actionow.common.core.result.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * Agent 配置服务接口
 *
 * @author Actionow
 */
public interface AgentConfigService {

    /**
     * 创建 Agent 配置
     *
     * @param request 创建请求
     * @return 创建的配置
     */
    AgentConfigResponse create(AgentConfigRequest request);

    /**
     * 更新 Agent 配置
     *
     * @param id      配置 ID
     * @param request 更新请求
     * @return 更新后的配置
     */
    AgentConfigResponse update(String id, AgentConfigRequest request);

    /**
     * 根据 ID 查询
     *
     * @param id 配置 ID
     * @return 配置
     */
    Optional<AgentConfigResponse> findById(String id);

    /**
     * 根据 ID 查询（必须存在）
     *
     * @param id 配置 ID
     * @return 配置
     */
    AgentConfigResponse getById(String id);

    /**
     * 根据 ID 查询实体
     *
     * @param id 配置 ID
     * @return 配置实体
     */
    AgentConfigEntity getEntityById(String id);

    /**
     * 根据 Agent 类型查询
     *
     * @param agentType Agent 类型
     * @return 配置
     */
    Optional<AgentConfigResponse> findByAgentType(String agentType);

    /**
     * 根据 Agent 类型查询（必须存在）
     *
     * @param agentType Agent 类型
     * @return 配置
     */
    AgentConfigResponse getByAgentType(String agentType);

    /**
     * 根据 Agent 类型查询实体
     *
     * @param agentType Agent 类型
     * @return 配置实体
     */
    AgentConfigEntity getEntityByAgentType(String agentType);

    /**
     * 删除配置
     *
     * @param id 配置 ID
     */
    void delete(String id);

    /**
     * 查询所有启用的配置
     *
     * @return 配置列表
     */
    List<AgentConfigResponse> findAllEnabled();

    /**
     * 分页查询
     *
     * @param current       当前页码
     * @param size          每页大小
     * @param agentType     Agent 类型（可选）
     * @param enabled       是否启用（可选）
     * @param llmProviderId LLM Provider ID（可选）
     * @return 分页结果
     */
    PageResult<AgentConfigResponse> findPage(Long current, Long size, String agentType, Boolean enabled, String llmProviderId);

    /**
     * 启用/禁用配置
     *
     * @param id      配置 ID
     * @param enabled 是否启用
     */
    void toggleEnabled(String id, Boolean enabled);

    /**
     * 获取配置版本历史
     *
     * @param id 配置 ID
     * @return 版本历史列表
     */
    List<AgentConfigVersionResponse> getVersionHistory(String id);

    /**
     * 回滚到指定版本
     *
     * @param id            配置 ID
     * @param versionNumber 目标版本号
     * @return 回滚后的配置
     */
    AgentConfigResponse rollback(String id, Integer versionNumber);

    /**
     * 检查是否有热更新
     *
     * @return 是否有更新
     */
    boolean hasHotUpdates();

    /**
     * 强制刷新缓存
     */
    void refreshCache();

    /**
     * 触发热更新
     * 通过 Redis Pub/Sub 通知所有服务实例清除缓存并重建 Agent
     *
     * @param agentType Agent 类型（可为空，表示所有 Agent）
     */
    void triggerHotReload(String agentType);

    /**
     * 同步缓存版本号
     * 在 Agent 重建后调用，将本地版本号同步为远端版本号，防止重复重建
     */
    void syncCacheVersion();

    /**
     * 获取当前 Redis 缓存版本号
     * 用于双重版本检查机制，防止重建期间配置变更导致的更新丢失
     *
     * @return 当前 Redis 版本号
     */
    long getCurrentRemoteVersion();

    /**
     * 获取构建完整的提示词（包含 includes）
     *
     * @param agentType Agent 类型
     * @return 完整的提示词
     */
    String getResolvedPrompt(String agentType);

    // ==================== 自定义 Agent 支持 ====================

    /**
     * 查询指定作用域的所有 Agent 配置
     *
     * @param scope       作用域 (SYSTEM, WORKSPACE, USER)
     * @param workspaceId 工作空间 ID（scope=WORKSPACE 时必填）
     * @param userId      用户 ID（scope=USER 时必填）
     * @return 配置列表
     */
    List<AgentConfigResponse> findByScope(String scope, String workspaceId, String userId);

    /**
     * 查询用户可用的所有 Agent（包括系统级、工作空间级、用户级）
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 可用的 Agent 配置列表
     */
    List<AgentConfigResponse> findAvailableAgents(String workspaceId, String userId);

    /**
     * 查询所有协调者 Agent
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 协调者 Agent 列表
     */
    List<AgentConfigResponse> findCoordinators(String workspaceId, String userId);

    /**
     * 查询所有支持独立调用的 Agent
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 独立调用 Agent 列表
     */
    List<AgentConfigResponse> findStandaloneAgents(String workspaceId, String userId);

    /**
     * 查询指定协调者的子 Agent 配置
     *
     * @param coordinatorAgentType 协调者 Agent 类型
     * @return 子 Agent 配置列表
     */
    List<AgentConfigEntity> findSubAgents(String coordinatorAgentType);

    /**
     * 查询所有启用的 Agent 实体（用于 Agent 构建）
     *
     * @return Agent 实体列表
     */
    List<AgentConfigEntity> findAllEnabledEntities();
}
