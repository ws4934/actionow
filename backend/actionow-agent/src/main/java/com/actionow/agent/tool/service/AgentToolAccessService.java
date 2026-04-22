package com.actionow.agent.tool.service;

import com.actionow.agent.tool.dto.AgentToolAccessRequest;
import com.actionow.agent.tool.dto.AgentToolAccessResponse;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.common.core.result.PageResult;

import java.util.List;

/**
 * Agent 工具访问权限服务接口
 *
 * @author Actionow
 */
public interface AgentToolAccessService {

    /**
     * 创建工具权限
     *
     * @param request 创建请求
     * @return 创建的权限
     */
    AgentToolAccessResponse create(AgentToolAccessRequest request);

    /**
     * 批量创建工具权限
     *
     * @param requests 请求列表
     * @return 创建的权限列表
     */
    List<AgentToolAccessResponse> createBatch(List<AgentToolAccessRequest> requests);

    /**
     * 更新工具权限
     *
     * @param id      权限 ID
     * @param request 更新请求
     * @return 更新后的权限
     */
    AgentToolAccessResponse update(String id, AgentToolAccessRequest request);

    /**
     * 根据 ID 查询
     *
     * @param id 权限 ID
     * @return 权限
     */
    AgentToolAccessResponse getById(String id);

    /**
     * 删除权限
     *
     * @param id 权限 ID
     */
    void delete(String id);

    /**
     * 分页查询
     *
     * @param current      当前页码
     * @param size         每页大小
     * @param agentType    Agent 类型（可选）
     * @param toolCategory 工具分类（可选）
     * @param toolId       工具 ID（可选）
     * @param enabled      是否启用（可选）
     * @return 分页结果
     */
    PageResult<AgentToolAccessResponse> findPage(Long current, Long size,
                                                  String agentType, String toolCategory,
                                                  String toolId, Boolean enabled);

    /**
     * 根据 Agent 类型查询所有工具权限
     *
     * @param agentType Agent 类型
     * @return 权限列表
     */
    List<AgentToolAccessResponse> getByAgentType(String agentType);

    /**
     * 根据 Agent 类型和分类查询
     *
     * @param agentType    Agent 类型
     * @param toolCategory 工具分类
     * @return 权限列表
     */
    List<AgentToolAccessResponse> getByAgentTypeAndCategory(String agentType, String toolCategory);

    /**
     * 根据工具 ID 查询（哪些 Agent 可使用）
     *
     * @param toolId 工具 ID
     * @return 权限列表
     */
    List<AgentToolAccessResponse> getByToolId(String toolId);

    /**
     * 启用/禁用权限
     *
     * @param id      权限 ID
     * @param enabled 是否启用
     */
    void toggleEnabled(String id, Boolean enabled);

    /**
     * 检查 Agent 是否有权限访问工具
     *
     * @param agentType    Agent 类型
     * @param toolCategory 工具分类
     * @param toolId       工具 ID
     * @return 是否有权限
     */
    boolean hasAccess(String agentType, String toolCategory, String toolId);

    /**
     * 检查配额
     *
     * @param agentType    Agent 类型
     * @param toolCategory 工具分类
     * @param toolId       工具 ID
     * @param userId       用户 ID
     * @return 是否在配额内
     */
    boolean checkQuota(String agentType, String toolCategory, String toolId, String userId);

    /**
     * 增加工具调用计数
     *
     * @param userId       用户 ID
     * @param toolCategory 工具分类
     * @param toolId       工具 ID
     * @return 当前调用次数
     */
    long incrementQuotaCount(String userId, String toolCategory, String toolId);

    /**
     * 获取 Agent 可用的所有工具信息
     *
     * @param agentType Agent 类型
     * @param userId    用户 ID（用于配额检查）
     * @return 工具信息列表
     */
    List<ToolInfo> getAvailableTools(String agentType, String userId);

    /**
     * 获取 Agent 在指定工具集合中的可用工具信息。
     *
     * @param agentType Agent 类型
     * @param userId    用户 ID（用于配额检查）
     * @param toolIds   待过滤的工具 ID 列表
     * @return 工具信息列表
     */
    List<ToolInfo> getAvailableTools(String agentType, String userId, List<String> toolIds);

    /**
     * 刷新缓存
     */
    void refreshCache();
}
