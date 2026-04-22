package com.actionow.agent.resolution.dto;

import com.actionow.agent.tool.dto.ToolInfo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 解析后的 Agent 运行配置。
 */
@Data
@Builder
public class ResolvedAgentProfile {

    private String agentType;

    private String agentName;

    private Boolean coordinator;

    private Boolean standaloneEnabled;

    private String workspaceId;

    private String userId;

    private String llmProviderId;

    private String resolvedPrompt;

    private String skillLoadMode;

    private String executionMode;

    private List<String> defaultSkillNames;

    private List<String> allowedSkillNames;

    private List<String> requestedSkillNames;

    private List<String> resolvedSkillNames;

    private List<ResolvedSkillInfo> resolvedSkills;

    private List<String> subAgentTypes;

    private List<String> resolvedToolIds;

    private List<ToolInfo> resolvedTools;
}
