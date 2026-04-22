package com.actionow.agent.core.scope;

import com.actionow.agent.config.constant.AgentExecutionMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 执行上下文
 * 包含当前会话的作用域信息，用于 Tool 调用时的权限校验
 *
 * @author Actionow
 */
@Data
@Builder(toBuilder = true)
public class AgentContext {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * Agent 类型（用于工具权限和配额检查）
     */
    private String agentType;

    /**
     * 本次执行已解析的 Skill 名称快照。
     */
    private List<String> skillNames;

    /**
     * 执行模式（CHAT / MISSION）。
     */
    private String executionMode;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 租户 ID (tenantId = workspaceId)
     */
    private String tenantId;

    /**
     * 作用域类型
     */
    private AgentScope scope;

    // ============ 锚点 ID ============

    /**
     * 剧本 ID（SCRIPT 作用域必填）
     */
    private String scriptId;

    /**
     * 剧本名称（缓存，避免 ContextAugmentationService 每轮重复 Feign 调用）
     */
    private String scriptName;

    /**
     * 章节 ID（上下文信息，可选）
     */
    private String episodeId;

    /**
     * 分镜 ID（上下文信息，可选）
     */
    private String storyboardId;

    /**
     * 角色 ID（上下文信息，可选）
     */
    private String characterId;

    /**
     * 场景 ID（上下文信息，可选）
     */
    private String sceneId;

    /**
     * 道具 ID（上下文信息，可选）
     */
    private String propId;

    /**
     * 风格 ID（上下文信息，可选）
     */
    private String styleId;

    /**
     * 素材 ID（上下文信息，可选）
     */
    private String assetId;

    /**
     * Mission ID（Mission 模式下可用）。
     */
    private String missionId;

    /**
     * Mission Step ID（Mission 模式下可用）。
     */
    private String missionStepId;

    /**
     * 判断是否为全局作用域
     */
    public boolean isGlobalScope() {
        return scope == AgentScope.GLOBAL;
    }

    /**
     * 判断指定剧本是否在当前作用域内
     */
    public boolean isScriptAccessible(String targetScriptId) {
        if (scope == AgentScope.GLOBAL) {
            return true;
        }
        return scriptId != null && scriptId.equals(targetScriptId);
    }

    /**
     * 获取当前作用域锚定的资源 ID
     */
    public String getAnchorResourceId() {
        return switch (scope) {
            case SCRIPT -> scriptId;
            case GLOBAL -> null;
        };
    }

    /**
     * 获取作用域描述信息
     */
    public String getScopeDescription() {
        String anchorId = getAnchorResourceId();
        if (anchorId == null) {
            return "全局（可访问所有资源）";
        }
        return scope.getName() + "「" + anchorId + "」";
    }

    public AgentExecutionMode getExecutionModeEnum() {
        return AgentExecutionMode.fromCode(executionMode);
    }

    public boolean isMissionExecution() {
        return getExecutionModeEnum() == AgentExecutionMode.MISSION;
    }
}
