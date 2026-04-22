package com.actionow.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.agent.constant.SessionStatus;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 会话实体
 * 存储在租户 Schema 中（tenant_xxx）
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_session", autoResultMap = true)
public class AgentSessionEntity extends BaseEntity {

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * Agent 类型 (scriptwriting 等)
     */
    private String agentType;

    /**
     * 作用域类型: global, script, episode, storyboard, character, scene, prop, style, asset
     * 决定 Agent 在本次对话中的数据可见范围
     */
    private String scope;

    /**
     * 作用域上下文 (JSONB)
     * 存储各类 ID 锚点: scriptId, episodeId, storyboardId, characterId, sceneId, propId, styleId, assetId
     */
    @TableField(value = "scope_context", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> scopeContext;

    /**
     * 会话状态: ACTIVE(活跃), ARCHIVED(已归档), DELETED(已删除)
     * @see com.actionow.agent.constant.SessionStatus
     */
    private String status;

    /**
     * 会话标题（从首条消息生成）
     */
    private String title;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * 总 Token 使用量
     */
    private Long totalTokens;

    /**
     * 扩展属性
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extras;

    /**
     * 会话级 Skill 名称列表（null = 加载全部启用 Skill；空列表 = 不加载任何 Skill）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> skillNames;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

    /**
     * "正在生成"起始时间（非 NULL 表示当前 session 有活跃 ReAct 循环）。
     * <p>仅在 skip-placeholder 路径下使用 —— 替代空 placeholder 消息行作为 session 级别的
     * 生成状态标记。Preflight 置位，Teardown 清空；心跳调度器依赖该列判定是否需要刷新
     * {@link #lastHeartbeatAt}。
     */
    private LocalDateTime generatingSince;

    /**
     * 最近一次心跳时间（session 级）。
     * <p>与 {@code t_agent_message.last_heartbeat_at} 对偶：后者附着在 placeholder 行（旧路径），
     * 前者附着在 session（skip-placeholder 新路径）。跨 pod 重连时 {@code /state} 端点
     * 从 session 读取心跳，不再依赖空 placeholder 行。
     */
    private LocalDateTime lastHeartbeatAt;

    /**
     * 归档时间（用户手动归档或系统自动归档时设置）
     */
    private LocalDateTime archivedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 判断是否已删除
     */
    public boolean getIsDeleted() {
        return getDeleted() != null && getDeleted() == 1;
    }

    /**
     * 设置删除状态
     */
    public void setIsDeleted(boolean deleted) {
        setDeleted(deleted ? 1 : 0);
    }

    /**
     * 判断是否为活跃状态
     */
    public boolean isActive() {
        return SessionStatus.fromCode(status).isActive();
    }

    // ============ scope_context 便捷存取方法 ============

    private Map<String, Object> ensureScopeContext() {
        if (scopeContext == null) {
            scopeContext = new HashMap<>();
        }
        return scopeContext;
    }

    private String getScopeField(String key) {
        return scopeContext != null ? (String) scopeContext.get(key) : null;
    }

    private void setScopeField(String key, String value) {
        if (value != null) {
            ensureScopeContext().put(key, value);
        } else if (scopeContext != null) {
            scopeContext.remove(key);
        }
    }

    public String getScriptId()     { return getScopeField("scriptId"); }
    public String getEpisodeId()    { return getScopeField("episodeId"); }
    public String getStoryboardId() { return getScopeField("storyboardId"); }
    public String getCharacterId()  { return getScopeField("characterId"); }
    public String getSceneId()      { return getScopeField("sceneId"); }
    public String getPropId()       { return getScopeField("propId"); }
    public String getStyleId()      { return getScopeField("styleId"); }
    public String getAssetId()      { return getScopeField("assetId"); }

    public void setScriptId(String v)     { setScopeField("scriptId", v); }
    public void setEpisodeId(String v)    { setScopeField("episodeId", v); }
    public void setStoryboardId(String v) { setScopeField("storyboardId", v); }
    public void setCharacterId(String v)  { setScopeField("characterId", v); }
    public void setSceneId(String v)      { setScopeField("sceneId", v); }
    public void setPropId(String v)       { setScopeField("propId", v); }
    public void setStyleId(String v)      { setScopeField("styleId", v); }
    public void setAssetId(String v)      { setScopeField("assetId", v); }

    /**
     * 判断是否为归档状态
     */
    public boolean isArchived() {
        return SessionStatus.fromCode(status) == SessionStatus.ARCHIVED;
    }

    /**
     * 获取会话状态枚举
     */
    public SessionStatus getStatusEnum() {
        return SessionStatus.fromCode(status);
    }

    /**
     * 设置会话状态
     */
    public void setStatusEnum(SessionStatus sessionStatus) {
        this.status = sessionStatus.getCode();
    }

    /**
     * 判断是否可以发送消息
     */
    public boolean canSendMessage() {
        return isActive() && !getIsDeleted();
    }

    /**
     * 判断是否可以恢复
     */
    public boolean canResume() {
        return isArchived() && !getIsDeleted();
    }
}
