package com.actionow.agent.saa.factory;

import com.actionow.common.core.exception.BusinessException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 不可用的 ChatModel 实现（SAA v2）
 * 当 LLM Provider 配置不完整（如 API Key 缺失）时使用此模型
 *
 * @author Actionow
 */
public class UnavailableChatModel implements ChatModel, StreamingChatModel {

    private final String llmProviderId;
    private final String reason;

    public UnavailableChatModel(String llmProviderId, String reason) {
        this.llmProviderId = llmProviderId;
        this.reason = reason;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        throw new BusinessException(String.format(
                "模型不可用 [%s]: %s。请检查 LLM Provider 配置，确保 API Key 已正确设置。",
                llmProviderId, reason));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.error(new BusinessException(String.format(
                "模型不可用 [%s]: %s。请检查 LLM Provider 配置，确保 API Key 已正确设置。",
                llmProviderId, reason)));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }

    public String getReason() { return reason; }
    public String getLlmProviderId() { return llmProviderId; }
    public boolean isAvailable() { return false; }
}
