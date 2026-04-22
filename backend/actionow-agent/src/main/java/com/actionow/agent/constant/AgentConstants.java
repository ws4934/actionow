package com.actionow.agent.constant;

/**
 * Agent 模块常量定义
 *
 * @author Actionow
 */
public final class AgentConstants {

    private AgentConstants() {}

    // ==================== 会话状态 ====================
    public static final String SESSION_STATUS_ACTIVE = "active";
    public static final String SESSION_STATUS_GENERATING = "generating";
    public static final String SESSION_STATUS_COMPLETED = "completed";
    public static final String SESSION_STATUS_EXPIRED = "expired";

    // ==================== 消息角色 ====================
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_TOOL_CALL = "tool_call";
    public static final String ROLE_TOOL_RESULT = "tool_result";

    // ==================== 消息状态 ====================
    public static final String MESSAGE_STATUS_GENERATING = "generating";
    public static final String MESSAGE_STATUS_COMPLETED = "completed";
    public static final String MESSAGE_STATUS_FAILED = "failed";
    public static final String MESSAGE_STATUS_CANCELLED = "cancelled";

    // ==================== 事件类型 ====================
    public static final String EVENT_THINKING = "thinking";
    public static final String EVENT_TOOL_CALL = "tool_call";
    public static final String EVENT_TOOL_RESULT = "tool_result";
    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_DONE = "done";
    public static final String EVENT_CANCELLED = "cancelled";
    /**
     * 结构化阶段状态事件 — 替代硬编码的 "Rolling..." 文本，
     * 前端可据此渲染进度条 / 阶段标签 / 步骤时间轴。
     * 载荷通过 AgentStreamEvent.metadata 传递：phase / label / progress / details。
     */
    public static final String EVENT_STATUS = "status";
    /**
     * 结构化数据事件 — 独立于普通 message 文本的可解析数据通路。
     * 用于 output_structured_result 的返回、复杂 UI 组件渲染。
     * 载荷通过 metadata 传递：schemaRef / data / rendererHint。
     */
    public static final String EVENT_STRUCTURED_DATA = "structured_data";
    /**
     * 请用户选择/确认事件 — HITL 弹窗的前端载体。
     * 载荷通过 metadata 传递：askId / question / choices / inputType / deadline。
     * 本期仅定义事件类型；真正的阻塞-唤醒机制由 P2 阶段实现。
     */
    public static final String EVENT_ASK_USER = "ask_user";
    /**
     * 心跳事件 — 在 generating 过程中每 N 秒下发一次，
     * 前端据此判断后端仍在活跃生成；若长时间无心跳可提示"疑似卡死"并触发 state 端点探测。
     * 载荷：metadata.lastHeartbeatAt（ISO 时间）、metadata.elapsedMs。
     */
    public static final String EVENT_HEARTBEAT = "heartbeat";
    /**
     * 回放间隙事件 — 当客户端 Last-Event-ID 已经落在 bridge 环形缓冲之外（过期 / 溢出）时下发，
     * 告知前端"增量回放不可能"，必须主动调 /agent/sessions/{id}/state + /messages 重新对齐。
     * 载荷：metadata.clientLastEventId（客户端声明的 lastId）、
     *      metadata.oldestAvailableEventId（当前缓冲最老 id，null 表示缓冲为空）、
     *      metadata.serverMaxEventId（服务端当前最大 id）。
     */
    public static final String EVENT_RESYNC_REQUIRED = "resync_required";

    /**
     * Agent 流式事件类型枚举
     * 提供类型安全的事件类型定义
     */
    public enum EventType {
        THINKING(EVENT_THINKING),
        TOOL_CALL(EVENT_TOOL_CALL),
        TOOL_RESULT(EVENT_TOOL_RESULT),
        MESSAGE(EVENT_MESSAGE),
        ERROR(EVENT_ERROR),
        DONE(EVENT_DONE),
        CANCELLED(EVENT_CANCELLED),
        STATUS(EVENT_STATUS),
        STRUCTURED_DATA(EVENT_STRUCTURED_DATA),
        ASK_USER(EVENT_ASK_USER),
        HEARTBEAT(EVENT_HEARTBEAT),
        RESYNC_REQUIRED(EVENT_RESYNC_REQUIRED);

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * 从字符串值解析事件类型
         */
        public static EventType fromValue(String value) {
            for (EventType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown event type: " + value);
        }

        /**
         * 判断是否为终止事件
         */
        public boolean isTerminal() {
            return this == DONE || this == ERROR || this == CANCELLED;
        }

        /**
         * 判断是否为工具相关事件
         */
        public boolean isToolEvent() {
            return this == TOOL_CALL || this == TOOL_RESULT;
        }
    }

    // ==================== 向量文档类型 ====================
    public static final String DOC_TYPE_KNOWLEDGE = "KNOWLEDGE";
    public static final String DOC_TYPE_HISTORY = "HISTORY";
    public static final String DOC_TYPE_STYLE = "STYLE";

    // ==================== 默认配置 ====================
    /**
     * ReAct 循环最大迭代次数
     * 基于 Gemini 3 Flash Preview 大容量上下文
     */
    public static final int DEFAULT_MAX_ITERATIONS = 20;

    /**
     * RAG 检索 Top-K
     */
    public static final int DEFAULT_TOP_K = 10;

    /**
     * Embedding 维度 (text-embedding-001)
     */
    public static final int DEFAULT_EMBEDDING_DIMENSION = 768;

    /**
     * 对话历史最大消息数
     * 基于 1M token 容量，按每条消息平均 1000 token 计算
     */
    public static final int DEFAULT_MAX_HISTORY_MESSAGES = 1000;

    // ==================== 多模态附件限制 ====================
    /** 每条消息最大附件数 */
    public static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;
    /** 单个附件最大大小（20MB） */
    public static final long MAX_ATTACHMENT_SIZE_BYTES = 20 * 1024 * 1024;
    /** 附件总大小上限（50MB） */
    public static final long MAX_TOTAL_ATTACHMENT_SIZE_BYTES = 50 * 1024 * 1024;

    // ==================== URL 下载相关 ====================
    /** URL 下载读取超时（秒） */
    public static final int URL_DOWNLOAD_TIMEOUT_SECONDS = 30;
    /** URL 附件默认视频大小估算（无 fileSize 时的 token 估算 fallback） */
    public static final long DEFAULT_VIDEO_SIZE_ESTIMATE = 5 * 1024 * 1024;    // 5MB
    /** URL 附件默认音频大小估算 */
    public static final long DEFAULT_AUDIO_SIZE_ESTIMATE = 2 * 1024 * 1024;    // 2MB
    /** URL 附件默认文档大小估算 */
    public static final long DEFAULT_DOCUMENT_SIZE_ESTIMATE = 100 * 1024;      // 100KB
}
