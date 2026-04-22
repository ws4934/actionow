package com.actionow.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 消息实体
 * 存储在租户 Schema 中（tenant_xxx）
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_message", autoResultMap = true)
public class AgentMessage extends BaseEntity {

    /**
     * 所属会话 ID
     */
    private String sessionId;

    /**
     * 消息角色: user, assistant, tool, system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 工具调用 ID（用于关联 tool_call 和 tool_result）
     */
    private String toolCallId;

    /**
     * 工具名称（当 role=tool 时）
     */
    private String toolName;

    /**
     * 工具调用参数（当 role=tool 时）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> toolArguments;

    /**
     * 工具执行结果（当 role=tool 时）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> toolResult;

    /**
     * Token 使用量
     */
    private Integer tokenCount;

    /**
     * 消息状态: generating / completed / failed / cancelled
     * NULL 等价于 completed（兼容历史数据）
     */
    private String status;

    /**
     * 消息序号
     */
    private Integer sequence;

    /**
     * 扩展属性
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extras;

    /**
     * 附件 ID 列表（关联已上传的 Asset 素材）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentIds;

    /**
     * 最近一次心跳时间（仅 status=generating 的占位消息使用）。
     * 由 AgentHeartbeatScheduler 定期更新，用于跨 pod 重连场景下 /state 端点估算活跃度。
     */
    private LocalDateTime lastHeartbeatAt;

    /**
     * 来源 SSE 事件的 eventId（由 AgentStreamBridge 按 session 单调分配）。
     * <p>仅 per-segment-write 路径下的 assistant_segment 行会落该列；runner 重连 / 跨 pod
     * 重放时用 {@code (session_id, event_id)} 做 app 层 dedup 检查（见
     * {@code SaaSessionService#appendAssistantSegment}），避免同一事件被重复 insert。
     * <p>与 {@code extras.eventId} 冗余保留：前者是查询索引键，后者是审计 / 埋点元数据。
     */
    private Long eventId;
}
