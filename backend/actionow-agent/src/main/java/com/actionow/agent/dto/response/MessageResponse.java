package com.actionow.agent.dto.response;

import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.entity.AgentMessage;
import com.actionow.agent.saa.session.AssistantSegmentSplitter;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息响应
 *
 * @author Actionow
 */
@Data
@Builder
public class MessageResponse {

    /**
     * 消息 ID
     */
    private String id;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 消息角色
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息序号
     */
    private Integer sequence;

    /**
     * 消息状态: generating / completed / failed / cancelled
     */
    private String status;

    /**
     * 事件类型（流式响应时）
     */
    private String eventType;

    /**
     * 工具调用 ID（用于关联 tool_call 和 tool_result）
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具所属的技能名称（便于前端按技能分组/着色）
     */
    private String skillName;

    /**
     * 当前事件来源的 Agent 名称（coordinator / universal-agent / 等）
     */
    private String agentName;

    /**
     * 工具参数
     */
    private Map<String, Object> toolArguments;

    /**
     * 工具执行是否成功（tool_result 事件）
     */
    private Boolean toolSuccess;

    /**
     * 工具结果
     */
    private Map<String, Object> toolResult;

    /**
     * 迭代次数
     */
    private Integer iteration;

    /**
     * 扩展属性
     */
    private Map<String, Object> metadata;

    // ==================== 统计字段（eventType 为 done 时） ====================

    /**
     * 执行耗时（毫秒）
     */
    private Long elapsedMs;

    /**
     * 工具调用总次数
     */
    private Integer totalToolCalls;

    /**
     * 估算的 token 消耗
     */
    private Long estimatedTokens;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 附件 ID 列表（关联已上传的 Asset 素材）
     */
    private List<String> attachmentIds;

    /**
     * 附件详情列表（含 url/fileName/mimeType 等，由 getMessages 接口填充）
     */
    private List<AttachmentInfo> attachments;

    /**
     * 从流事件转换
     */
    public static MessageResponse from(AgentStreamEvent event) {
        if (event == null) {
            return null;
        }
        return MessageResponse.builder()
                .eventType(event.getType())
                .content(event.getContent())
                .toolCallId(event.getToolCallId())
                .toolName(event.getToolName())
                .skillName(event.getSkillName())
                .agentName(event.getAgentName())
                .toolArguments(event.getToolArguments())
                .toolSuccess(event.getToolSuccess())
                .toolResult(event.getToolResult())
                .iteration(event.getIteration())
                .metadata(event.getMetadata())
                .elapsedMs(event.getElapsedMs())
                .totalToolCalls(event.getTotalToolCalls())
                .estimatedTokens(event.getEstimatedTokens())
                .timestamp(event.getTimestamp())
                .build();
    }

    /**
     * 从实体转换
     * 为工具消息自动设置 eventType，确保与 SSE 返回格式一致
     */
    public static MessageResponse from(AgentMessage entity) {
        if (entity == null) {
            return null;
        }

        String role = entity.getRole();
        String normalizedRole = role;
        // 推断 eventType：对于 tool 角色的消息，根据 toolResult 是否存在判断是 tool_call 还是 tool_result
        String eventType = null;
        if ("tool".equals(role)) {
            if (entity.getToolResult() != null && !entity.getToolResult().isEmpty()) {
                eventType = "tool_result";
            } else if (entity.getToolArguments() != null && !entity.getToolArguments().isEmpty()) {
                eventType = "tool_call";
            }
        } else if ("tool_call".equals(role)) {
            normalizedRole = "tool";
            eventType = "tool_call";
        } else if ("tool_result".equals(role)) {
            normalizedRole = "tool";
            eventType = "tool_result";
        } else if ("user".equals(role)) {
            eventType = "message";
        } else if ("assistant".equals(role)) {
            eventType = "message";
        }

        return MessageResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(normalizedRole)
                .content(entity.getContent())
                .sequence(entity.getSequence())
                .status(entity.getStatus() != null ? entity.getStatus() : "completed")
                .eventType(eventType)
                .toolCallId(entity.getToolCallId())
                .toolName(entity.getToolName())
                .toolArguments(entity.getToolArguments())
                .toolResult(entity.getToolResult())
                .metadata(entity.getExtras())
                .attachmentIds(entity.getAttachmentIds())
                .createdAt(entity.getCreatedAt())
                .timestamp(entity.getCreatedAt())
                .build();
    }

    /**
     * 把单条 assistant 实体扩展为多段 —— 仅当 content 可按段落切出 >1 段时生效。
     *
     * <p>用于兼容 Step 2 前写入的旧数据（整段 blob 的 placeholder 消息）：
     * 前端看到的就是"每段一气泡"，与 Step 2 后每 message event 独立落行的新数据同构，
     * 从而消除"两套渲染逻辑"。不改变 DB 数据、无副作用。
     *
     * <p>对于非 assistant 角色或只有单段的消息，返回单元素列表（逻辑等价于 {@link #from(AgentMessage)}）。
     *
     * <p>拆出的子段共享原 sequence（前端仍用 sequence 排序稳定，子段内用 {@code segmentIndex} 细排），
     * id 形如 {@code {原id}#{segmentIndex}}，{@code metadata.segmentIndex} / {@code metadata.segmentTotal} 标识。
     */
    public static List<MessageResponse> expandAssistant(AgentMessage entity) {
        if (entity == null) return List.of();
        if (!"assistant".equals(entity.getRole())) {
            return List.of(from(entity));
        }
        // 正在生成的占位消息：保留为单条"空 assistant"标记，前端可据此显示打字光标
        if ("generating".equals(entity.getStatus())) {
            return List.of(from(entity));
        }
        // 每段独立写入模式下，placeholder 在 finalize 时仅更新状态不覆盖 content，
        // 导致 DB 中残留一条 content="" + status=completed 的空行；前端看到会是空气泡。
        // 这里直接过滤：真正的正文由对应的 assistant_segment 行承载。
        if ((entity.getContent() == null || entity.getContent().isBlank())
                && !hasToolPayload(entity)) {
            return List.of();
        }
        List<String> parts = AssistantSegmentSplitter.split(entity.getContent());
        if (parts.size() <= 1) {
            return List.of(from(entity));
        }
        List<MessageResponse> expanded = new ArrayList<>(parts.size());
        int total = parts.size();
        for (int i = 0; i < total; i++) {
            MessageResponse response = from(entity);
            response.setId(entity.getId() + "#" + i);
            response.setContent(parts.get(i));
            Map<String, Object> metadata = response.getMetadata() != null
                    ? new java.util.LinkedHashMap<>(response.getMetadata())
                    : new java.util.LinkedHashMap<>();
            metadata.put("segmentIndex", i);
            metadata.put("segmentTotal", total);
            metadata.put("segmentOriginId", entity.getId());
            // 标记"由只读适配器拆出"的来源，便于前端埋点 / 后续迁移比对
            metadata.put("segmentSource", "legacy_split");
            response.setMetadata(metadata);
            expanded.add(response);
        }
        return expanded;
    }

    private static boolean hasToolPayload(AgentMessage entity) {
        return (entity.getToolCallId() != null && !entity.getToolCallId().isBlank())
                || (entity.getToolArguments() != null && !entity.getToolArguments().isEmpty())
                || (entity.getToolResult() != null && !entity.getToolResult().isEmpty());
    }

    /**
     * 创建错误响应
     */
    public static MessageResponse error(String errorMessage) {
        return MessageResponse.builder()
                .eventType("error")
                .content(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
