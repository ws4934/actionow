package com.actionow.agent.context;

import com.actionow.agent.config.service.AgentConfigService;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.core.scope.AgentContext;
import com.actionow.agent.core.scope.AgentContextHolder;
import com.actionow.agent.tool.annotation.ChatDirectTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Layer 3: manual compact 工具
 * 暴露 compact_context 供用户或 Agent 主动触发上下文压缩。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextTools {

    private final ContextWindowManager contextWindowManager;
    private final AgentConfigService agentConfigService;

    @ChatDirectTool
    @Tool(name = "compact_context",
            description = "主动压缩当前会话的上下文。当对话过长或 Agent 感知到上下文可能溢出时调用。返回压缩前后的 token 统计。")
    public String compactContext() {
        String sessionId = SessionContextHolder.getCurrentSessionId();
        if (sessionId == null) {
            return "无法获取当前会话 ID，请在对话中使用此工具";
        }

        String llmProviderId = resolveLlmProviderId();
        if (llmProviderId == null) {
            return "无法获取当前模型信息，上下文压缩失败";
        }

        try {
            int[] stats = contextWindowManager.forceCompact(sessionId, llmProviderId);
            log.info("Manual compact 完成: sessionId={}, before={}, after={}", sessionId, stats[0], stats[1]);
            return String.format("上下文已压缩: %d tokens → %d tokens (节省 %d tokens)",
                    stats[0], stats[1], stats[0] - stats[1]);
        } catch (Exception e) {
            log.error("Manual compact 失败: sessionId={}", sessionId, e);
            return "上下文压缩失败: " + e.getMessage();
        }
    }

    @org.springframework.lang.Nullable
    private String resolveLlmProviderId() {
        AgentContext ctx = AgentContextHolder.getContext();
        if (ctx == null || ctx.getAgentType() == null) {
            return null;
        }
        return agentConfigService.findByAgentType(ctx.getAgentType())
                .map(config -> config.getLlmProviderId())
                .orElse(null);
    }
}
