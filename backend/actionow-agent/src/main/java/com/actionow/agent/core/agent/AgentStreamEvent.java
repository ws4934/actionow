package com.actionow.agent.core.agent;

import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.constant.AgentConstants.EventType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 流式事件
 * 用于实时推送执行状态
 *
 * @author Actionow
 */
@Data
@Builder
public class AgentStreamEvent {

    /**
     * 事件类型
     * @see AgentConstants#EVENT_THINKING
     * @see AgentConstants#EVENT_TOOL_CALL
     * @see AgentConstants#EVENT_TOOL_RESULT
     * @see AgentConstants#EVENT_MESSAGE
     * @see AgentConstants#EVENT_ERROR
     * @see AgentConstants#EVENT_DONE
     * @see AgentConstants#EVENT_CANCELLED
     */
    private String type;

    /**
     * 单调递增的事件序号，由 {@code AgentStreamBridge} 在 publish 时分配。
     * 客户端断线重连时用作 {@code Last-Event-ID} 回放依据；该字段在产生端通常为 null，
     * 只有经由 bridge 落入 SSE 之前会被赋值，以保持 per-session 单调。
     */
    private Long eventId;

    /**
     * 当前活跃的 Agent 名称
     * 用于标识事件来源于哪个 Agent（协调器或专家）
     */
    private String agentName;

    /**
     * 事件内容
     */
    private String content;

    /**
     * 工具调用 ID（用于关联 tool_call 和 tool_result）
     */
    private String toolCallId;

    /**
     * 工具名称（tool_call/tool_result 事件）
     */
    private String toolName;

    /**
     * 工具所属的技能名称（tool_call/tool_result 事件）
     */
    private String skillName;

    /**
     * 工具参数（tool_call 事件）
     */
    private Map<String, Object> toolArguments;

    /**
     * 工具执行是否成功（tool_result 事件）
     */
    private Boolean toolSuccess;

    /**
     * 工具执行结果（tool_result 事件）
     */
    private Map<String, Object> toolResult;

    /**
     * 当前迭代轮次
     */
    private Integer iteration;

    /**
     * 事件时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 扩展数据/元数据
     */
    private Map<String, Object> metadata;

    // ==================== 统计字段（done 事件） ====================

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
     * 是否为因错误而中断的部分响应
     * 用于标识 ADK 内部错误等情况下的不完整响应
     */
    private Boolean partialDueToError;

    /**
     * 错误消息
     * 当 partialDueToError=true 时，用于向用户显示友好的错误提示
     */
    private String errorMessage;

    // ==================== 类型安全方法 ====================

    /**
     * 获取类型安全的事件类型枚举
     */
    public EventType getEventType() {
        return EventType.fromValue(type);
    }

    /**
     * 判断是否为终止事件（done/error/cancelled）
     */
    public boolean isTerminal() {
        return AgentConstants.EVENT_DONE.equals(type)
                || AgentConstants.EVENT_ERROR.equals(type)
                || AgentConstants.EVENT_CANCELLED.equals(type);
    }

    /**
     * 判断是否为工具相关事件
     */
    public boolean isToolEvent() {
        return AgentConstants.EVENT_TOOL_CALL.equals(type)
                || AgentConstants.EVENT_TOOL_RESULT.equals(type);
    }

    /**
     * 判断是否为消息事件
     */
    public boolean isMessage() {
        return AgentConstants.EVENT_MESSAGE.equals(type);
    }

    /**
     * 判断是否为思考事件
     */
    public boolean isThinking() {
        return AgentConstants.EVENT_THINKING.equals(type);
    }

    /**
     * 判断是否为错误事件
     */
    public boolean isError() {
        return AgentConstants.EVENT_ERROR.equals(type);
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建思考事件
     */
    public static AgentStreamEvent thinking(String content, int iteration) {
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_THINKING)
                .content(content)
                .iteration(iteration)
                .build();
    }

    /**
     * 创建工具调用事件
     */
    public static AgentStreamEvent toolCall(String toolCallId, String toolName,
                                               Map<String, Object> arguments, int iteration,
                                               String skillName) {
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_TOOL_CALL)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .toolArguments(arguments)
                .iteration(iteration)
                .skillName(skillName)
                .build();
    }

    /**
     * 创建工具结果事件
     * 注意：toolResult 字段格式与 saveToolResultMessage 保持一致，确保 SSE 和 messages API 数据一致
     */
    public static AgentStreamEvent toolResult(String toolCallId, String toolName, boolean success,
                                                 String content, int iteration, String skillName) {
        // 构建标准化的 toolResult 结构，与数据库存储格式一致
        Map<String, Object> toolResultMap = new HashMap<>();
        toolResultMap.put("success", success);
        toolResultMap.put("output", content != null ? content : "");

        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_TOOL_RESULT)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .skillName(skillName)
                .toolSuccess(success)
                .toolResult(toolResultMap)
                .content(content)
                .iteration(iteration)
                .build();
    }

    /**
     * 创建消息事件（增量文本）
     */
    public static AgentStreamEvent message(String content) {
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_MESSAGE)
                .content(content)
                .build();
    }

    /**
     * 创建错误事件
     */
    public static AgentStreamEvent error(String errorMessage) {
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_ERROR)
                .content(errorMessage)
                .build();
    }

    /**
     * 创建完成事件
     */
    public static AgentStreamEvent done(int totalIterations) {
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_DONE)
                .iteration(totalIterations)
                .build();
    }

    /**
     * 创建完成事件（带统计数据）
     *
     * @param totalIterations 总迭代次数
     * @param elapsedMs       执行耗时（毫秒）
     * @param totalToolCalls  工具调用次数
     * @param estimatedTokens 估算的 token 消耗
     */
    public static AgentStreamEvent doneWithStats(int totalIterations, long elapsedMs,
                                                  int totalToolCalls, long estimatedTokens) {
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_DONE)
                .iteration(totalIterations)
                .elapsedMs(elapsedMs)
                .totalToolCalls(totalToolCalls)
                .estimatedTokens(estimatedTokens)
                .build();
    }

    /**
     * 创建取消事件
     *
     * @param partialContent 取消时已生成的部分内容
     * @param iteration 取消时的迭代轮次
     */
    public static AgentStreamEvent cancelled(String partialContent, int iteration) {
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_CANCELLED)
                .content(partialContent)
                .iteration(iteration)
                .build();
    }

    /**
     * 创建结构化阶段状态事件 — 替代硬编码 "Rolling..." 文本。
     * 用于向前端推送可渲染的阶段标签 + 进度百分比。
     *
     * @param phase     阶段 key（机器可读：skill_loading / tool_batch_progress / mission_step / generating / ...）
     * @param label     用户可见的描述文本
     * @param progress  0.0-1.0 之间的进度；未知填 null
     * @param details   额外上下文（如 skillName / currentStep / totalSteps），可为 null
     */
    public static AgentStreamEvent status(String phase, String label, Double progress,
                                          Map<String, Object> details) {
        Map<String, Object> md = new HashMap<>();
        if (phase != null)    md.put("phase", phase);
        if (label != null)    md.put("label", label);
        if (progress != null) md.put("progress", progress);
        if (details != null)  md.putAll(details);
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_STATUS)
                .content(label)
                .metadata(md)
                .build();
    }

    /**
     * 创建结构化数据事件 — 把 output_structured_result 等结构化输出从 message 文本里分流出来，
     * 前端可据 schemaRef 匹配组件，据 rendererHint 决定渲染方式（table / card / form / chart）。
     *
     * @param schemaRef     schema 引用（通常是 skillName.outputSchema 或全局约定 key）
     * @param data          结构化数据本身
     * @param rendererHint  渲染提示，可为 null；约定值：table / card / form / chart / markdown
     */
    public static AgentStreamEvent structuredData(String schemaRef, Map<String, Object> data,
                                                  String rendererHint) {
        Map<String, Object> md = new HashMap<>();
        if (schemaRef != null)    md.put("schemaRef", schemaRef);
        if (data != null)         md.put("data", data);
        if (rendererHint != null) md.put("rendererHint", rendererHint);
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_STRUCTURED_DATA)
                .metadata(md)
                .build();
    }

    /**
     * 创建 "请用户选择/确认" 事件 — HITL 弹窗的前端载体。
     * 本期仅定义事件；真正的 agent-阻塞 / resume API 由 P2 阶段接入 SAA checkpoint 实现。
     *
     * @param askId      唯一请求 ID（用作后续 POST /ask/{askId}/answer 的关联键）
     * @param question   面向用户的问题文本
     * @param choices    可选项列表；每项 {id, label, description?}。null 或空则为自由文本输入
     * @param inputType  约定值：single_choice / multi_choice / confirm / text / number
     * @param deadlineMs 超时毫秒数；null 或 <=0 表示无截止时间
     */
    public static AgentStreamEvent askUser(String askId, String question,
                                           java.util.List<Map<String, Object>> choices,
                                           String inputType, Long deadlineMs) {
        Map<String, Object> md = new HashMap<>();
        if (askId != null)      md.put("askId", askId);
        if (question != null)   md.put("question", question);
        if (choices != null)    md.put("choices", choices);
        if (inputType != null)  md.put("inputType", inputType);
        if (deadlineMs != null && deadlineMs > 0) md.put("deadlineMs", deadlineMs);
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_ASK_USER)
                .content(question)
                .metadata(md)
                .build();
    }

    /**
     * 回放间隙事件 — 客户端携带的 lastEventId 已落出缓冲窗口，
     * 增量回放不可能，必须走 /state + /messages 全量对齐。
     *
     * @param clientLastEventId       客户端声明的 lastEventId
     * @param oldestAvailableEventId  当前缓冲最老 eventId，null 表示缓冲为空
     * @param serverMaxEventId        服务端当前最大 eventId
     */
    public static AgentStreamEvent resyncRequired(long clientLastEventId,
                                                   Long oldestAvailableEventId,
                                                   long serverMaxEventId) {
        Map<String, Object> md = new HashMap<>();
        md.put("clientLastEventId", clientLastEventId);
        md.put("oldestAvailableEventId", oldestAvailableEventId);
        md.put("serverMaxEventId", serverMaxEventId);
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_RESYNC_REQUIRED)
                .metadata(md)
                .build();
    }

    /**
     * 心跳事件工厂 — 在 generating 过程中定期下发，告知前端后端仍在活跃处理。
     *
     * @param elapsedMs 距生成开始的毫秒数
     */
    public static AgentStreamEvent heartbeat(long elapsedMs) {
        Map<String, Object> md = new HashMap<>();
        md.put("elapsedMs", elapsedMs);
        md.put("serverTime", LocalDateTime.now().toString());
        return AgentStreamEvent.builder()
                .type(AgentConstants.EVENT_HEARTBEAT)
                .metadata(md)
                .build();
    }

    /**
     * Token 使用统计
     */
    @Data
    @Builder
    public static class TokenUsage {
        /**
         * 输入 token 数（prompt）
         */
        private Long promptTokens;

        /**
         * 输出 token 数（completion）
         */
        private Long completionTokens;

        /**
         * 缓存命中的 token 数
         */
        private Long cachedTokens;

        /**
         * 思考过程的 token 数
         */
        private Long thoughtTokens;

        /**
         * 工具使用的 prompt token 数
         */
        private Long toolUsePromptTokens;

        /**
         * 总 token 数
         */
        private Long totalTokens;

        /**
         * 是否为估算值
         * true: 从字符长度估算（ADK 未返回 usageMetadata 时）
         * false/null: 来自 ADK usageMetadata 的实际值
         */
        private Boolean estimated;

        /**
         * 转换为 Map（用于序列化）
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (promptTokens != null) {
                map.put("promptTokens", promptTokens);
            }
            if (completionTokens != null) {
                map.put("completionTokens", completionTokens);
            }
            if (cachedTokens != null) {
                map.put("cachedTokens", cachedTokens);
            }
            if (thoughtTokens != null) {
                map.put("thoughtTokens", thoughtTokens);
            }
            if (toolUsePromptTokens != null) {
                map.put("toolUsePromptTokens", toolUsePromptTokens);
            }
            if (totalTokens != null) {
                map.put("totalTokens", totalTokens);
            }
            if (estimated != null && estimated) {
                map.put("estimated", true);
            }
            return map;
        }
    }
}
