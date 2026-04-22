package com.actionow.agent.saa.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * SystemMessage 合并拦截器 — 兼容 Gemini 的 "only one system message" 限制。
 *
 * <p>背景：SAA 的 {@code AgentLlmNode.appendSystemPromptIfNeeded} 会把
 * {@code ModelRequest.systemMessage}（来自 {@code ReactAgent.builder().systemPrompt(...)}）
 * 盲目 prepend 到 {@code messages} 列表最前。如果 {@code messages} 里已经有 SystemMessage
 * （比如 CHECKPOINT 历史行 + ContextWindowManager 注入的 contextSystemPrompt），最终发给
 * {@link org.springframework.ai.google.genai.GoogleGenAiChatModel} 的 Prompt 就会有 2+ 条
 * SystemMessage，触发 {@code Assert.isTrue("Only one system message is allowed")}。
 *
 * <p>本拦截器在 LLM 调用前做一次"规范化"：
 * <ol>
 *   <li>从 {@code request.getMessages()} 中提取所有 SystemMessage</li>
 *   <li>和 {@code request.getSystemMessage()} 按出现顺序用双空行拼成一条</li>
 *   <li>用拼好的 SystemMessage 替换 {@code systemMessage}，
 *       用过滤掉 SystemMessage 后的 messages 替换 {@code messages}</li>
 * </ol>
 *
 * <p>这样 {@code appendSystemPromptIfNeeded} 只会 prepend 这一条，下游模型收到唯一 SystemMessage。
 * 对 OpenAI / Anthropic 语义等价（多 system 等价于单 system 的拼接）。
 *
 * @author Actionow
 */
@Slf4j
public class SystemMessageCoalesceInterceptor extends ModelInterceptor {

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler next) {
        List<Message> messages = request.getMessages();
        SystemMessage baseSystem = request.getSystemMessage();

        long systemInMessages = messages == null ? 0
                : messages.stream().filter(m -> m instanceof SystemMessage).count();

        // 快速路径：messages 里没有 SystemMessage，无需改写
        if (systemInMessages == 0) {
            return next.call(request);
        }

        StringBuilder merged = new StringBuilder();
        if (baseSystem != null && baseSystem.getText() != null && !baseSystem.getText().isBlank()) {
            merged.append(baseSystem.getText());
        }

        List<Message> filtered = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            if (msg instanceof SystemMessage sys) {
                String text = sys.getText();
                if (text == null || text.isBlank()) continue;
                if (merged.length() > 0) merged.append("\n\n");
                merged.append(text);
            } else {
                filtered.add(msg);
            }
        }

        SystemMessage coalesced = merged.length() > 0 ? new SystemMessage(merged.toString()) : null;

        log.debug("SystemMessageCoalesceInterceptor: merged {} SystemMessage(s) from messages + base={}",
                systemInMessages, baseSystem != null);

        ModelRequest rewritten = ModelRequest.builder(request)
                .systemMessage(coalesced)
                .messages(filtered)
                .build();
        return next.call(rewritten);
    }

    @Override
    public String getName() {
        return "SystemMessageCoalesceInterceptor";
    }
}
