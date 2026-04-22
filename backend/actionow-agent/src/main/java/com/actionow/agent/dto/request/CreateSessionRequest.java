package com.actionow.agent.dto.request;

import com.actionow.agent.constant.AgentType;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建会话请求
 *
 * @author Actionow
 */
@Data
public class CreateSessionRequest {

    /**
     * Agent 类型（可选，默认 COORDINATOR）
     * 使用 AgentType 枚举的 code 值
     *
     * @see AgentType
     */
    private String agentType = AgentType.COORDINATOR.getCode();

    /**
     * 作用域类型
     * 默认为 global（全局作用域）
     * <p>
     * 层级作用域:
     * - global: 可访问当前用户/工作空间下的所有资源
     * - script: 仅可访问指定剧本及其子资源
     * - episode: 仅可访问指定章节及其子资源
     * - storyboard: 仅可访问指定分镜
     * <p>
     * 实体作用域:
     * - character: 仅可访问指定角色
     * - scene: 仅可访问指定场景
     * - prop: 仅可访问指定道具
     * - style: 仅可访问指定风格
     * - asset: 仅可访问指定素材
     */
    private String scope = "global";

    /**
     * 作用域上下文（JSONB 存储）
     * 可包含: scriptId, episodeId, storyboardId, characterId, sceneId, propId, styleId, assetId
     */
    private Map<String, Object> scopeContext;

    /**
     * 初始上下文（可选）
     */
    private Map<String, Object> initialContext;

    /**
     * 会话级 Skill 名称列表（可选）
     * null = 加载全部启用 Skill；[] = 关闭所有 Skill（纯文本模式）
     */
    private List<String> skillNames;

    // ============ 向后兼容：支持前端直接传 scriptId 等顶层字段 ============

    /**
     * 当前端传入 {"scriptId": "xxx"} 顶层字段时（旧格式），
     * 自动合并到 scopeContext 中，确保向后兼容。
     */
    @JsonSetter("scriptId")
    public void setScriptId(String scriptId) {
        if (scriptId != null) {
            ensureScopeContext().put("scriptId", scriptId);
        }
    }

    @JsonSetter("episodeId")
    public void setEpisodeId(String episodeId) {
        if (episodeId != null) {
            ensureScopeContext().put("episodeId", episodeId);
        }
    }

    @JsonSetter("storyboardId")
    public void setStoryboardId(String storyboardId) {
        if (storyboardId != null) {
            ensureScopeContext().put("storyboardId", storyboardId);
        }
    }

    @JsonSetter("characterId")
    public void setCharacterId(String characterId) {
        if (characterId != null) {
            ensureScopeContext().put("characterId", characterId);
        }
    }

    @JsonSetter("sceneId")
    public void setSceneId(String sceneId) {
        if (sceneId != null) {
            ensureScopeContext().put("sceneId", sceneId);
        }
    }

    @JsonSetter("propId")
    public void setPropId(String propId) {
        if (propId != null) {
            ensureScopeContext().put("propId", propId);
        }
    }

    @JsonSetter("styleId")
    public void setStyleId(String styleId) {
        if (styleId != null) {
            ensureScopeContext().put("styleId", styleId);
        }
    }

    @JsonSetter("assetId")
    public void setAssetId(String assetId) {
        if (assetId != null) {
            ensureScopeContext().put("assetId", assetId);
        }
    }

    // ============ 便捷方法 ============

    public String getScriptId() {
        return scopeContext != null ? (String) scopeContext.get("scriptId") : null;
    }

    private Map<String, Object> ensureScopeContext() {
        if (scopeContext == null) {
            scopeContext = new HashMap<>();
        }
        return scopeContext;
    }
}
