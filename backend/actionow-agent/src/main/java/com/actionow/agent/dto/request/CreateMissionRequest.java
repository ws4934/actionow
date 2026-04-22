package com.actionow.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 Mission 请求
 *
 * @author Actionow
 */
@Data
public class CreateMissionRequest {

    /**
     * Mission 标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 300, message = "标题不能超过300个字符")
    private String title;

    /**
     * 用户原始请求（目标）
     */
    @NotBlank(message = "目标不能为空")
    private String goal;

    /**
     * 初始计划（可选）
     */
    private Map<String, Object> plan;

    /**
     * Mission 内部运行时会话 ID（可选，不传则自动创建）
     */
    private String runtimeSessionId;

    /**
     * 创建时快照的租户 Schema（由 MissionTools 自动填充）
     */
    private String tenantSchema;

    /**
     * 创建时快照的 Agent 类型（由 MissionTools 自动填充）
     */
    private String agentType;

    /**
     * 创建时快照的 Skill 名称列表（由 MissionTools 自动填充）
     */
    private List<String> skillNames;
}
