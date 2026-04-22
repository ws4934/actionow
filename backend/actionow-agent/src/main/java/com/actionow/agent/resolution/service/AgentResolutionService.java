package com.actionow.agent.resolution.service;

import com.actionow.agent.resolution.dto.ResolvedAgentProfile;

import java.util.List;

/**
 * Agent 解析服务。
 */
public interface AgentResolutionService {

    /**
     * 解析指定上下文下的 Agent 运行配置。
     */
    ResolvedAgentProfile resolve(String agentType, String workspaceId, String userId, List<String> requestedSkillNames);
}
