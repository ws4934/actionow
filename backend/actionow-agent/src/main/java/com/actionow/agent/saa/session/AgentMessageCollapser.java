package com.actionow.agent.saa.session;

import com.actionow.agent.dto.response.MessageResponse;
import com.actionow.agent.entity.AgentMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 相邻同类消息合并器 —— 让"一气呵成的 assistant 文本段落"在前端展示为一条气泡，
 * 而工具调用 / 用户输入作为天然段落边界自然打断合并组。
 *
 * <h2>背景</h2>
 * Step 2 写入侧改造让每个 {@code EVENT_MESSAGE} 独立落一行 assistant 段落，DB 写入粒度
 * 与 SSE 事件一一对齐，但 ReAct 一次迭代里 LLM 可能发多个 text event（逐段、逐 delta），
 * 不合并就会在前端出现 5-10 个连续小气泡的视觉碎片。
 *
 * <p>本合并器运行在 <strong>读侧</strong>（{@code GET /messages} 和 LLM 历史回灌两个入口），
 * 不改写 DB：底层事件行保持原粒度作为审计流，渲染 / 回灌时才 collapse。
 *
 * <h2>合并规则</h2>
 * <ol>
 *   <li>分组键 = {@code role + eventType + extras.kind}；</li>
 *   <li>只合并 {@code role=assistant} 且 {@code extras.kind=assistant_segment} 的相邻行；</li>
 *   <li>遇到以下行立刻 flush 当前累积组：
 *       <ul>
 *         <li>{@code role=user}（新 turn 开始）</li>
 *         <li>{@code role=tool}（工具 pair 作为天然段落边界）</li>
 *         <li>其它 {@code role=assistant} 但 {@code kind != assistant_segment}（如 MissionCard 投影消息）</li>
 *         <li>{@code status != completed} 的 assistant 行（未完成的段落不合并，保留"打字光标"语义）</li>
 *       </ul>
 *   </li>
 *   <li>工具 pair / 其它行原样放行，不做任何处理。</li>
 * </ol>
 *
 * <h2>合并语义</h2>
 * <ul>
 *   <li>id / sequence / createdAt / iteration：取首条；</li>
 *   <li>content：多段用 {@code \n\n} 连接并 trim；</li>
 *   <li>metadata：叠加 {@code collapsed=true} / {@code collapsedCount} /
 *       {@code collapsedEventIds[]}（来自 extras.eventId，缺失则置 null 占位），
 *       方便前端埋点 / 调试 / 反向对齐原始事件流。</li>
 * </ul>
 *
 * <h2>幂等性 / 纯函数</h2>
 * 方法无副作用；对已经合并过的输入（单条组）返回原样列表。允许在"段落切分器扩展后"紧接着调用，
 * 也允许直接应用于未扩展的列表。失败时调用方应 fallback 到原始列表（本类抛异常则视为配置/数据异常）。
 *
 * @author Actionow
 */
public final class AgentMessageCollapser {

    private static final String KIND_ASSISTANT_SEGMENT = "assistant_segment";
    private static final String SEGMENT_SEPARATOR = "\n\n";

    private AgentMessageCollapser() {}

    /**
     * 扫描并合并相邻的 assistant_segment 行。
     *
     * @param input 已按 sequence 升序排序的消息列表（允许含 legacy split 后的子段）
     * @return 合并后的新列表；不修改入参
     */
    public static List<MessageResponse> collapse(List<MessageResponse> input) {
        return collapse(input, null);
    }

    /**
     * 带 group-size 观测回调的 collapse —— 每 flush 一个合并组（含 size=1 的单元素组）
     * 就调用 {@code groupSizeSink.accept(size)}。调用方可用于指标埋点 / 日志调试。
     * {@code sink} 为 null 时行为等同无参版本。
     */
    public static List<MessageResponse> collapse(List<MessageResponse> input, IntConsumer groupSizeSink) {
        if (input == null || input.isEmpty()) return input;
        List<MessageResponse> result = new ArrayList<>(input.size());
        List<MessageResponse> pending = new ArrayList<>();

        for (MessageResponse row : input) {
            if (isMergeable(row)) {
                pending.add(row);
            } else {
                flush(pending, result, groupSizeSink);
                result.add(row);
            }
        }
        flush(pending, result, groupSizeSink);
        return result;
    }

    /**
     * 判定一行是否参与合并组。命中条件：
     * role=assistant + status=completed + extras.kind=assistant_segment。
     * 其它一律当作"边界 / 不合并"。
     *
     * <p>legacy_split 出来的段落（metadata.segmentSource=legacy_split）也视为 assistant_segment
     * 的等价物 —— 旧数据切出的连续段落同样会被合并回一条，与新数据表现一致。
     */
    private static boolean isMergeable(MessageResponse row) {
        if (row == null) return false;
        if (!"assistant".equals(row.getRole())) return false;
        String status = row.getStatus();
        if (status != null && !"completed".equals(status)) return false;
        Map<String, Object> metadata = row.getMetadata();
        if (metadata == null) return false;
        Object kind = metadata.get("kind");
        Object segmentSource = metadata.get("segmentSource");
        return KIND_ASSISTANT_SEGMENT.equals(kind) || "legacy_split".equals(segmentSource);
    }

    /**
     * 把 pending 队列里的多段合并成一条 push 进 result；队列随即清空。
     * 每次 flush（含 size=1 的单元素组）都会调用 {@code sink.accept(size)}，方便
     * 外层收集合并组大小分布指标。{@code sink} 为 null 时跳过。
     */
    private static void flush(List<MessageResponse> pending, List<MessageResponse> result, IntConsumer sink) {
        if (pending.isEmpty()) return;
        int size = pending.size();
        if (size == 1) {
            result.add(pending.get(0));
        } else {
            result.add(mergeAll(pending));
        }
        if (sink != null) sink.accept(size);
        pending.clear();
    }

    private static MessageResponse mergeAll(List<MessageResponse> group) {
        MessageResponse first = group.get(0);
        // 用首条作为"基底"，content / metadata 替换为合并结果；其余字段（id / sequence /
        // createdAt / iteration / toolCallId 等）天然继承首条。
        StringBuilder content = new StringBuilder();
        List<Object> eventIds = new ArrayList<>(group.size());
        for (int i = 0; i < group.size(); i++) {
            MessageResponse row = group.get(i);
            String part = row.getContent() != null ? row.getContent().trim() : "";
            if (!part.isEmpty()) {
                if (content.length() > 0) content.append(SEGMENT_SEPARATOR);
                content.append(part);
            }
            Object eventId = row.getMetadata() != null ? row.getMetadata().get("eventId") : null;
            eventIds.add(eventId); // 允许 null 占位，保持与 group 同长
        }

        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (first.getMetadata() != null) {
            mergedMetadata.putAll(first.getMetadata());
        }
        mergedMetadata.put("collapsed", true);
        mergedMetadata.put("collapsedCount", group.size());
        mergedMetadata.put("collapsedEventIds", eventIds);

        return MessageResponse.builder()
                .id(first.getId())
                .sessionId(first.getSessionId())
                .role(first.getRole())
                .content(content.toString())
                .sequence(first.getSequence())
                .status(first.getStatus())
                .eventType(first.getEventType())
                .toolCallId(first.getToolCallId())
                .toolName(first.getToolName())
                .skillName(first.getSkillName())
                .agentName(first.getAgentName())
                .toolArguments(first.getToolArguments())
                .toolSuccess(first.getToolSuccess())
                .toolResult(first.getToolResult())
                .iteration(first.getIteration())
                .metadata(mergedMetadata)
                .elapsedMs(first.getElapsedMs())
                .totalToolCalls(first.getTotalToolCalls())
                .estimatedTokens(first.getEstimatedTokens())
                .timestamp(first.getTimestamp())
                .createdAt(first.getCreatedAt())
                .attachmentIds(first.getAttachmentIds())
                .attachments(first.getAttachments())
                .build();
    }

    // ---------------------------------------------------------------------
    // AgentMessage 实体侧的合并 —— 用于 LLM 历史回灌（ContextWindowManager）
    // 规则与 MessageResponse 版本保持一致：同为 role=assistant + status completed/null
    // + extras.kind=assistant_segment 的相邻行合并为一条，tool/user 作为天然边界。
    // ---------------------------------------------------------------------

    /**
     * 合并相邻 assistant_segment 实体。用于把"同一轮 ReAct 里分多段写入的 assistant 段落"
     * 还原为"一条完整 assistant turn"再喂给 LLM，避免模型看到自己的话被切成 5 段独白。
     *
     * @param input 已按 sequence 升序排序的 {@link AgentMessage} 列表
     * @return 合并后的新列表；不修改入参
     */
    public static List<AgentMessage> collapseEntities(List<AgentMessage> input) {
        return collapseEntities(input, null);
    }

    /**
     * 带 group-size 观测回调的 entity collapse —— 与 MessageResponse 版本一致，每 flush
     * 一个合并组就调用 {@code sink.accept(size)}。调用方（ContextWindowManager）用于
     * 记录 LLM 侧合并分布指标。
     */
    public static List<AgentMessage> collapseEntities(List<AgentMessage> input, IntConsumer groupSizeSink) {
        if (input == null || input.isEmpty()) return input;
        List<AgentMessage> result = new ArrayList<>(input.size());
        List<AgentMessage> pending = new ArrayList<>();

        for (AgentMessage row : input) {
            if (isMergeableEntity(row)) {
                pending.add(row);
            } else {
                flushEntities(pending, result, groupSizeSink);
                result.add(row);
            }
        }
        flushEntities(pending, result, groupSizeSink);
        return result;
    }

    private static boolean isMergeableEntity(AgentMessage row) {
        if (row == null) return false;
        if (!"assistant".equals(row.getRole())) return false;
        String status = row.getStatus();
        if (status != null && !"completed".equals(status)) return false;
        Map<String, Object> extras = row.getExtras();
        if (extras == null) return false;
        Object kind = extras.get("kind");
        Object segmentSource = extras.get("segmentSource");
        return KIND_ASSISTANT_SEGMENT.equals(kind) || "legacy_split".equals(segmentSource);
    }

    private static void flushEntities(List<AgentMessage> pending, List<AgentMessage> result, IntConsumer sink) {
        if (pending.isEmpty()) return;
        int size = pending.size();
        if (size == 1) {
            result.add(pending.get(0));
        } else {
            result.add(mergeAllEntities(pending));
        }
        if (sink != null) sink.accept(size);
        pending.clear();
    }

    private static AgentMessage mergeAllEntities(List<AgentMessage> group) {
        AgentMessage first = group.get(0);
        StringBuilder content = new StringBuilder();
        List<Object> eventIds = new ArrayList<>(group.size());
        int tokenSum = 0;
        boolean anyToken = false;
        for (AgentMessage row : group) {
            String part = row.getContent() != null ? row.getContent().trim() : "";
            if (!part.isEmpty()) {
                if (content.length() > 0) content.append(SEGMENT_SEPARATOR);
                content.append(part);
            }
            Object eventId = row.getExtras() != null ? row.getExtras().get("eventId") : null;
            eventIds.add(eventId);
            if (row.getTokenCount() != null) {
                tokenSum += row.getTokenCount();
                anyToken = true;
            }
        }

        AgentMessage merged = new AgentMessage();
        merged.setSessionId(first.getSessionId());
        merged.setRole(first.getRole());
        merged.setContent(content.toString());
        merged.setToolCallId(first.getToolCallId());
        merged.setToolName(first.getToolName());
        merged.setToolArguments(first.getToolArguments());
        merged.setToolResult(first.getToolResult());
        merged.setTokenCount(anyToken ? tokenSum : first.getTokenCount());
        merged.setStatus(first.getStatus());
        merged.setSequence(first.getSequence());
        merged.setAttachmentIds(first.getAttachmentIds());
        merged.setLastHeartbeatAt(first.getLastHeartbeatAt());
        // 复制 BaseEntity 字段（id / 时间戳等）以保持可追溯性
        merged.setId(first.getId());
        merged.setCreatedAt(first.getCreatedAt());
        merged.setUpdatedAt(first.getUpdatedAt());

        Map<String, Object> mergedExtras = new LinkedHashMap<>();
        if (first.getExtras() != null) {
            mergedExtras.putAll(first.getExtras());
        }
        mergedExtras.put("collapsed", true);
        mergedExtras.put("collapsedCount", group.size());
        mergedExtras.put("collapsedEventIds", eventIds);
        merged.setExtras(mergedExtras);

        return merged;
    }
}
