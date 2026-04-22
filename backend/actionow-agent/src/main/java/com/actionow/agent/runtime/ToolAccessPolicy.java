package com.actionow.agent.runtime;

import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.resolution.dto.ResolvedAgentProfile;

import java.util.List;

/**
 * 执行模式维度的工具访问策略。
 */
public interface ToolAccessPolicy {

    List<String> filterToolIds(AgentExecutionMode mode, ResolvedAgentProfile profile);
}
