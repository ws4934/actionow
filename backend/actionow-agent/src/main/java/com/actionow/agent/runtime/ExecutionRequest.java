package com.actionow.agent.runtime;

import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.resolution.dto.ResolvedAgentProfile;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Map;

/**
 * 纯运行时执行请求。
 */
@Data
@Builder
public class ExecutionRequest {

    private AgentExecutionMode mode;

    /**
     * Runtime/Memory 所使用的会话 ID。
     */
    private String sessionId;

    /**
     * 已解析的 Agent 配置。
     */
    private ResolvedAgentProfile resolvedAgent;

    /**
     * 已构建好的输入文本。
     */
    private String input;

    /**
     * 多模态媒体。
     */
    private List<Media> media;

    /**
     * 调用方已统计好的输入 token（可选）。
     */
    private Long inputTokens;

    /**
     * 当前执行模式对应的工具访问策略（可选）。
     */
    private ToolAccessPolicy toolAccessPolicy;

    /**
     * 含历史的完整消息列表（可选）。
     * CHAT 路径由 ContextWindowManager 构建，含历史 + 当前输入。
     * 设置后 input 字段仅用于 token 估算，实际消息以此为准。
     */
    private List<Message> contextMessages;

    /**
     * 工具名称 → 技能名称的反向映射（可选）。
     * 用于 SSE 事件中标识工具调用所属技能。
     */
    private Map<String, String> toolSkillMapping;

    /**
     * 缓存的 Agent 实例（可选）。
     * Mission 多步复用时由调用方传入，跳过重复构建。
     */
    private SupervisorAgent cachedAgent;
}
