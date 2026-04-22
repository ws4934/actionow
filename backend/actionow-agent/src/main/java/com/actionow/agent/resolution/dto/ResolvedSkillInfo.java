package com.actionow.agent.resolution.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 解析后的 Skill 信息。
 */
@Data
@Builder
public class ResolvedSkillInfo {

    private String name;

    private String displayName;

    private String description;

    private String scope;

    private String workspaceId;

    private Integer version;

    private Boolean enabled;

    private List<String> toolIds;
}
