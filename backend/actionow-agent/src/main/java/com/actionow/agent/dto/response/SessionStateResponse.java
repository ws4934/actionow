package com.actionow.agent.dto.response;

import com.actionow.agent.interaction.PendingAskResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话恢复状态响应。
 *
 * <p>前端进入对话页 / SSE 重连后调用 {@code GET /agent/sessions/{id}/state}
 * 获取一次性、权威的会话状态快照；据此决定：
 * <ul>
 *   <li>{@link ResumeHint#ANSWER_ASK} — 恢复 HITL 弹窗并允许用户提交答案</li>
 *   <li>{@link ResumeHint#RESUME_STREAM} — 订阅 SSE 继续消费流式增量</li>
 *   <li>{@link ResumeHint#IDLE} — 无进行中的交互，正常展示历史</li>
 * </ul>
 *
 * @author Actionow
 */
@Data
@Builder
public class SessionStateResponse {

    /** 会话基础元数据（标题/状态/计数）。 */
    private SessionInfo session;

    /** 当前生成状态（是否有占位消息在进行中）。 */
    private GenerationInfo generation;

    /** 当前等待用户回答的 HITL 请求；无则 pending=false。 */
    private PendingAskResponse pendingAsk;

    /**
     * 最近一条 SSE 事件的 ID。
     * <p>前端重连时作为 Last-Event-ID 回放依据。阶段 P1 尚未实现事件 ID 分配，
     * 固定返回 0；待 {@code #4 eventId + 回放} 落地后切换为真实值。
     */
    private Long lastEventId;

    /** 恢复动作提示，便于前端直接分发。 */
    private ResumeHint resumeHint;

    public enum ResumeHint {
        /** 无任何进行中交互。 */
        IDLE,
        /** 存在 generating 占位消息，应订阅 SSE 继续接收增量。 */
        RESUME_STREAM,
        /** 存在 PENDING ask，应优先渲染 HITL 弹窗并提交回答。 */
        ANSWER_ASK
    }

    @Data
    @Builder
    public static class SessionInfo {
        private String id;
        private String agentType;
        private String title;
        private String status;
        private Integer messageCount;
        private Long totalTokens;
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;
    }

    @Data
    @Builder
    public static class GenerationInfo {
        /** 是否存在进行中的生成（ExecutionRegistry 持有活跃执行，或 DB 有 generating 占位）。 */
        private boolean inFlight;
        /** 进行中的占位消息 ID；无则为 null。 */
        private String placeholderMessageId;
        /** 占位消息创建时间（DB 记录）。 */
        private LocalDateTime startedAt;
        /**
         * 最近一次心跳时间。P1 阶段占位返回 null；待 {@code #6 generating 心跳} 落地后填充。
         */
        private LocalDateTime lastHeartbeatAt;
        /** 距离最近活动的毫秒数（startedAt 至 now），用于前端判断是否疑似卡死。 */
        private Long staleMs;
    }
}
