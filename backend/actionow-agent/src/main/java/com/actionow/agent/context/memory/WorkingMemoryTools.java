package com.actionow.agent.context.memory;

import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.tool.annotation.AgentToolSpec;
import com.actionow.agent.tool.annotation.ChatDirectTool;
import com.actionow.agent.tool.annotation.ToolActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Working Memory 工具集
 * <p>
 * 提供 Agent 手动操作工作记忆的能力，类似 SAA 的 GetMemory / SaveMemory 工具。
 * 与 MicroCompactor 的自动存储配合：
 * - MicroCompactor 压缩 READ/SEARCH 结果时自动存入 Working Memory
 * - Agent 也可以手动存储/取回任意参考数据
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingMemoryTools {

    private final WorkingMemoryStore memoryStore;

    @ChatDirectTool
    @Tool(name = "save_to_memory",
            description = "将重要数据保存到工作记忆中。适用于需要在后续对话中引用的关键内容（如PDF全文、角色列表、剧本大纲等）。" +
                    "保存后即使上下文被压缩，也可以通过 recall_from_memory 取回完整内容。")
    @AgentToolSpec(
            displayName = "保存到工作记忆",
            summary = "将数据存入 session 级工作记忆",
            purpose = "防止重要参考数据因上下文压缩而丢失",
            actionType = ToolActionType.CONTROL,
            tags = {"memory", "context", "save"})
    public String saveToMemory(
            @ToolParam(description = "记忆键名，语义化命名（如 character_list, pdf_content, script_outline）") String key,
            @ToolParam(description = "要保存的完整内容") String value,
            @ToolParam(description = "是否固定（固定后不会被自动淘汰），默认 false", required = false) Boolean pinned) {

        String sessionId = SessionContextHolder.getCurrentSessionId();
        if (sessionId == null) {
            return "❌ 无法获取当前会话 ID";
        }
        if (key == null || key.isBlank()) {
            return "❌ key 不能为空";
        }
        if (value == null || value.isBlank()) {
            return "❌ value 不能为空";
        }

        boolean pin = pinned != null && pinned;
        memoryStore.put(sessionId, key.trim(), value, "agent", pin);

        return String.format("✅ 已保存到工作记忆: key=\"%s\" (%d chars%s)",
                key.trim(), value.length(), pin ? ", 已固定" : "");
    }

    @ChatDirectTool
    @Tool(name = "recall_from_memory",
            description = "从工作记忆中取回之前保存的完整数据。当上下文中出现 'details=omitted' 的压缩提示时，" +
                    "可通过此工具取回原始完整内容。")
    @AgentToolSpec(
            displayName = "从工作记忆取回",
            summary = "从 session 级工作记忆中读取数据",
            purpose = "取回因上下文压缩而省略的参考数据",
            actionType = ToolActionType.READ,
            tags = {"memory", "context", "recall"})
    public String recallFromMemory(
            @ToolParam(description = "记忆键名") String key) {

        String sessionId = SessionContextHolder.getCurrentSessionId();
        if (sessionId == null) {
            return "❌ 无法获取当前会话 ID";
        }
        if (key == null || key.isBlank()) {
            return "❌ key 不能为空";
        }

        WorkingMemoryStore.MemoryEntry entry = memoryStore.get(sessionId, key.trim());
        if (entry == null) {
            // 列出可用的 key 帮助 Agent 定位
            List<WorkingMemoryStore.MemoryEntry> all = memoryStore.list(sessionId);
            if (all.isEmpty()) {
                return String.format("❌ 工作记忆中不存在 key=\"%s\"，当前无任何缓存", key.trim());
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("❌ 工作记忆中不存在 key=\"%s\"。可用的 key:\n", key.trim()));
            for (WorkingMemoryStore.MemoryEntry e : all) {
                sb.append(String.format("  - %s (来源: %s, %d chars)\n",
                        e.getKey(), e.getSource(), e.getCharCount()));
            }
            return sb.toString();
        }

        return entry.getValue();
    }

    @ChatDirectTool
    @Tool(name = "list_memory",
            description = "列出工作记忆中所有已缓存的条目摘要（不含完整内容）。用于了解哪些数据可以通过 recall_from_memory 取回。")
    @AgentToolSpec(
            displayName = "查看工作记忆列表",
            summary = "列出 session 级工作记忆中的所有条目",
            purpose = "查看当前可用的缓存数据索引",
            actionType = ToolActionType.READ,
            tags = {"memory", "context", "list"})
    public String listMemory() {
        String sessionId = SessionContextHolder.getCurrentSessionId();
        if (sessionId == null) {
            return "❌ 无法获取当前会话 ID";
        }

        List<WorkingMemoryStore.MemoryEntry> entries = memoryStore.list(sessionId);
        if (entries.isEmpty()) {
            return "工作记忆为空，暂无缓存数据";
        }

        StringBuilder sb = new StringBuilder("工作记忆中的缓存条目:\n");
        for (WorkingMemoryStore.MemoryEntry entry : entries) {
            sb.append(String.format("  - key=\"%s\" | 来源: %s | %d chars | %s\n",
                    entry.getKey(),
                    entry.getSource() != null ? entry.getSource() : "unknown",
                    entry.getCharCount(),
                    entry.isPinned() ? "📌已固定" : "可淘汰"));
        }
        sb.append(String.format("\n共 %d 条。使用 recall_from_memory(key=\"<key>\") 获取完整内容。", entries.size()));
        return sb.toString();
    }
}
