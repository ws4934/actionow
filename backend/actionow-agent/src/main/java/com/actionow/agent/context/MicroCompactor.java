package com.actionow.agent.context;

import com.actionow.agent.context.memory.WorkingMemoryStore;
import com.actionow.agent.entity.AgentMessage;
import com.actionow.agent.tool.annotation.ToolActionType;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 1: micro-compact
 * 压缩旧的 tool_result，不动最近 N 轮。
 * 在内存中操作副本，不修改 DB 原始数据。
 * <p>
 * 感知工具类型：READ/SEARCH 类工具（参考数据）使用更高的压缩阈值，
 * WRITE/GENERATE 类工具（操作确认）使用默认阈值。
 * 避免因盲目截断参考数据导致 Agent 反复重新调用 READ 工具。
 *
 * @author Actionow
 */
@Slf4j
@Component
public class MicroCompactor {

    private final ProjectToolRegistry toolRegistry;
    private final WorkingMemoryStore workingMemoryStore;

    /** READ/SEARCH 类工具的 actionType 集合——其结果对后续推理有参考价值，应使用更宽松的压缩阈值 */
    private static final Set<String> REFERENCE_ACTION_TYPES = Set.of(
            ToolActionType.READ.name(),
            ToolActionType.SEARCH.name()
    );

    /** Working Memory 相关工具名，其结果不需要自动存入 Working Memory（避免循环存储） */
    private static final Set<String> MEMORY_TOOL_NAMES = Set.of(
            "recall_from_memory", "save_to_memory", "list_memory"
    );

    /** 缓存 toolName → actionType 映射，避免每条消息都查询 Registry */
    private final Map<String, String> actionTypeCache = new ConcurrentHashMap<>();

    @Value("${actionow.context.micro-compact-recent-rounds:3}")
    private int recentRounds;

    /** WRITE/GENERATE/CONTROL 类工具的压缩阈值（默认 300 chars） */
    @Value("${actionow.context.micro-compact-threshold-chars:300}")
    private int thresholdChars;

    /** READ/SEARCH 类工具的压缩阈值（默认 5000 chars），设为 0 表示不压缩 */
    @Value("${actionow.context.micro-compact-reference-threshold-chars:5000}")
    private int referenceThresholdChars;

    public MicroCompactor(ProjectToolRegistry toolRegistry, WorkingMemoryStore workingMemoryStore) {
        this.toolRegistry = toolRegistry;
        this.workingMemoryStore = workingMemoryStore;
    }

    /**
     * 压缩旧的 tool_result。
     * READ/SEARCH 类工具使用更宽松的阈值，避免参考数据过早被截断。
     * 当 READ/SEARCH 结果被压缩时，自动存入 Working Memory 供后续 recall。
     *
     * @param sessionId 会话 ID（用于将被压缩的参考数据存入 Working Memory）
     * @param messages  按 sequence 升序的完整消息列表
     * @return 压缩后的消息列表（最近 N 轮保持原样）
     */
    public List<AgentMessage> compact(String sessionId, List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        int recentBoundary = findRecentBoundary(messages, recentRounds);

        return messages.stream().map(msg -> {
            if (msg.getSequence() == null || msg.getSequence() >= recentBoundary) {
                return msg;
            }
            if (!"tool".equals(msg.getRole())) {
                return msg;
            }
            if (msg.getToolResult() == null) {
                return msg;
            }

            String output = String.valueOf(msg.getToolResult().getOrDefault("output", ""));

            // 根据工具类型选择压缩阈值
            String actionType = resolveActionType(msg.getToolName());
            boolean isReference = REFERENCE_ACTION_TYPES.contains(actionType);
            int effectiveThreshold = isReference ? referenceThresholdChars : thresholdChars;
            if (effectiveThreshold == 0 || output.length() <= effectiveThreshold) {
                return msg;
            }

            // READ/SEARCH 结果被压缩前，自动存入 Working Memory（排除 memory 工具自身，避免循环）
            boolean savedToMemory = false;
            String memoryKey = null;
            if (isReference && sessionId != null && !output.isBlank()
                    && !MEMORY_TOOL_NAMES.contains(msg.getToolName())) {
                memoryKey = buildMemoryKey(msg.getToolName(), msg.getToolCallId());
                workingMemoryStore.put(sessionId, memoryKey, output, msg.getToolName(), false);
                savedToMemory = true;
                log.debug("Auto-saved to working memory before compact: sessionId={}, key={}, tool={}, chars={}",
                        sessionId, memoryKey, msg.getToolName(), output.length());
            }

            // 构建压缩副本
            AgentMessage copy = shallowCopy(msg);
            boolean success = Boolean.TRUE.equals(msg.getToolResult().get("success"));
            String placeholder;
            if (savedToMemory) {
                // 包含 recall 提示，引导 Agent 从 Working Memory 取回
                placeholder = String.format(
                        "tool=%s success=%s details=omitted(%d chars) [已缓存到工作记忆 key=\"%s\"，可用 recall_from_memory 取回]",
                        msg.getToolName(), success, output.length(), memoryKey);
            } else {
                placeholder = String.format(
                        "tool=%s success=%s details=omitted(%d chars)",
                        msg.getToolName(), success, output.length());
            }
            copy.setContent(placeholder);
            copy.setToolResult(Map.of("success", success, "output", placeholder));
            copy.setTokenCount(null); // 标记需要重新估算
            return copy;
        }).toList();
    }

    /**
     * 根据 toolName + toolCallId 生成 Working Memory key。
     * 使用 toolName 作为 key 前缀，相同工具的多次调用用 callId 后缀区分。
     * 同名工具的后续调用会自动覆盖前一次，保持最新数据。
     */
    private String buildMemoryKey(String toolName, String toolCallId) {
        // 使用简洁的工具名作为 key，方便 Agent 按语义检索
        // 如果同一工具被多次调用，后面的会覆盖前面的（保持最新）
        return toolName != null ? toolName : "unknown_tool";
    }

    /**
     * 解析工具的 actionType。
     * 优先从 ProjectToolRegistry 查询，查不到时通过名称前缀推断。
     */
    private String resolveActionType(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return ToolActionType.UNKNOWN.name();
        }
        return actionTypeCache.computeIfAbsent(toolName, name ->
                toolRegistry.getProjectTool(name)
                        .map(ToolInfo::getActionType)
                        .filter(at -> at != null && !at.isBlank())
                        .orElseGet(() -> inferActionType(name))
        );
    }

    /**
     * 根据工具名称前缀推断 actionType（兜底策略，与 ProjectToolScanner 保持一致）。
     */
    private static String inferActionType(String toolName) {
        if (toolName == null) return ToolActionType.UNKNOWN.name();
        if (toolName.startsWith("get_") || toolName.startsWith("list_")) {
            return ToolActionType.READ.name();
        }
        if (toolName.startsWith("query_") || toolName.startsWith("search_")) {
            return ToolActionType.SEARCH.name();
        }
        return ToolActionType.UNKNOWN.name();
    }

    /**
     * 找到最近 N 轮 user 消息中最早一条的 sequence。
     * "一轮" = 一条 user 消息及其后续所有 assistant/tool 消息。
     */
    static int findRecentBoundary(List<AgentMessage> messages, int rounds) {
        int userCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                userCount++;
                if (userCount >= rounds) {
                    return msg.getSequence() != null ? msg.getSequence() : 0;
                }
            }
        }
        // 消息不足 N 轮，不压缩任何内容
        return 0;
    }

    private static AgentMessage shallowCopy(AgentMessage src) {
        AgentMessage copy = new AgentMessage();
        copy.setId(src.getId());
        copy.setSessionId(src.getSessionId());
        copy.setRole(src.getRole());
        copy.setContent(src.getContent());
        copy.setToolCallId(src.getToolCallId());
        copy.setToolName(src.getToolName());
        copy.setToolArguments(src.getToolArguments());
        copy.setToolResult(src.getToolResult());
        copy.setTokenCount(src.getTokenCount());
        copy.setStatus(src.getStatus());
        copy.setSequence(src.getSequence());
        copy.setExtras(src.getExtras());
        copy.setAttachmentIds(src.getAttachmentIds());
        return copy;
    }
}
