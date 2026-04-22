package com.actionow.agent.saa.factory;

import com.actionow.agent.feign.AiFeignClient;
import com.actionow.agent.feign.dto.LlmCredentialsResponse;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

/**
 * SAA ChatModel 工厂
 * 替代 v1 的 LlmClientFactory，直接返回 Spring AI {@link ChatModel}
 * 无需包装为 ADK BaseLlm
 *
 * 支持的 Provider:
 * - OpenAI / 豆包 / 智谱 / 月之暗面 / 通义千问 / DeepSeek（OpenAI兼容）
 * - Anthropic
 * - Google Gemini（通过 Spring AI Google GenAI）
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaaChatModelFactory {

    private final AiFeignClient aiFeignClient;

    /**
     * ChatModel 缓存 (llmProviderId → ChatModel)，TTL: 30 分钟
     */
    private final Cache<String, ChatModel> chatModelCache = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /**
     * 凭证缓存（避免重复 Feign 调用），TTL: 5 分钟
     */
    private final Cache<String, LlmCredentialsResponse> credentialsCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * 根据 LLM Provider ID 创建 Spring AI ChatModel
     *
     * @param llmProviderId LLM Provider ID
     * @return Spring AI ChatModel
     */
    public ChatModel createModel(String llmProviderId) {
        // Check availability before entering the model cache loader
        if (getOrFetchCredentials(llmProviderId) == null) {
            log.warn("无法获取 LLM 凭证，返回不可用模型: llmProviderId={}", llmProviderId);
            return new UnavailableChatModel(llmProviderId, "无法获取凭证");
        }
        // Loader always re-fetches credentials from credentialsCache (TTL 5 min) to avoid
        // building a new model with stale credentials when chatModelCache (TTL 30 min) expires.
        return chatModelCache.get(llmProviderId, key -> {
            LlmCredentialsResponse freshCreds = getOrFetchCredentials(key);
            return freshCreds != null
                    ? createSpringAiChatModel(freshCreds)
                    : new UnavailableChatModel(key, "凭证在模型构建时失效");
        });
    }

    /**
     * 创建不可用的模型占位符
     */
    public ChatModel createUnavailableModel(String identifier, String reason) {
        return new UnavailableChatModel(identifier, reason);
    }

    /**
     * 清除指定 Provider 的缓存
     */
    public void evictCache(String llmProviderId) {
        chatModelCache.invalidate(llmProviderId);
        credentialsCache.invalidate(llmProviderId);
        log.info("清除 LLM 缓存: llmProviderId={}", llmProviderId);
    }

    /**
     * 清除所有缓存
     */
    public void evictAllCache() {
        chatModelCache.invalidateAll();
        credentialsCache.invalidateAll();
        log.info("清除所有 LLM 缓存");
    }

    /**
     * 获取模型的上下文窗口大小（token 数）。
     */
    public int getContextWindow(String llmProviderId) {
        LlmCredentialsResponse creds = getOrFetchCredentials(llmProviderId);
        if (creds == null || creds.getContextWindow() == null) return 128000;
        return creds.getContextWindow();
    }

    /**
     * 获取模型的最大输入 token 数。
     */
    public int getMaxInputTokens(String llmProviderId) {
        LlmCredentialsResponse creds = getOrFetchCredentials(llmProviderId);
        if (creds == null || creds.getMaxInputTokens() == null) return 100000;
        return creds.getMaxInputTokens();
    }

    // ==================== 私有方法 ====================

    private ChatModel createSpringAiChatModel(LlmCredentialsResponse creds) {
        String provider = creds.getProvider().toUpperCase();

        if (!StringUtils.hasText(creds.getApiKey())) {
            log.warn("LLM Provider API Key 未配置: provider={}", provider);
            return new UnavailableChatModel(provider, "API Key 未配置");
        }

        log.info("创建 Spring AI ChatModel: provider={}, modelId={}", provider, creds.getModelId());

        try {
            return switch (provider) {
                case "OPENAI",
                     "VOLCENGINE",
                     "ZHIPU",
                     "MOONSHOT",
                     "ALIBABA",
                     "DASHSCOPE",
                     "DEEPSEEK" -> createOpenAiModel(creds);
                case "ANTHROPIC" -> createAnthropicModel(creds);
                case "GOOGLE", "GEMINI" -> createGeminiModel(creds);
                case "BAIDU" -> {
                    log.warn("百度文心使用特殊认证流程，Agent v2 暂不支持");
                    yield new UnavailableChatModel(provider, "百度文心暂不支持");
                }
                default -> {
                    log.warn("不支持的 LLM Provider: {}", provider);
                    yield new UnavailableChatModel(provider, "不支持的 Provider: " + provider);
                }
            };
        } catch (Exception e) {
            log.error("创建 ChatModel 失败: provider={}, error={}", provider, e.getMessage(), e);
            return new UnavailableChatModel(provider, "创建模型失败: " + e.getMessage());
        }
    }

    private ChatModel createOpenAiModel(LlmCredentialsResponse creds) {
        var options = OpenAiChatOptions.builder()
                .model(creds.getModelId())
                .temperature(safeDouble(creds.getTemperature(), 0.7))
                .maxTokens(creds.getMaxOutputTokens() != null ? creds.getMaxOutputTokens() : 8192)
                .topP(safeDouble(creds.getTopP(), 0.95))
                .build();

        // Use Reactor Netty with 180s read timeout to support complex multi-tool LLM calls
        HttpClient nettyClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)
                .responseTimeout(Duration.ofSeconds(180));
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(new ReactorClientHttpRequestFactory(nettyClient));

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(creds.getApiKey())
                .restClientBuilder(restClientBuilder);

        if (StringUtils.hasText(creds.getApiEndpoint())) {
            apiBuilder.baseUrl(creds.getApiEndpoint());
        }
        if (StringUtils.hasText(creds.getCompletionsPath())) {
            apiBuilder.completionsPath(creds.getCompletionsPath());
        }

        return OpenAiChatModel.builder()
                .openAiApi(apiBuilder.build())
                .defaultOptions(options)
                .build();
    }

    private ChatModel createAnthropicModel(LlmCredentialsResponse creds) {
        var options = AnthropicChatOptions.builder()
                .model(creds.getModelId())
                .temperature(safeDouble(creds.getTemperature(), 0.7))
                .maxTokens(creds.getMaxOutputTokens() != null ? creds.getMaxOutputTokens() : 8192)
                .topP(safeDouble(creds.getTopP(), 0.95))
                .topK(creds.getTopK() != null ? creds.getTopK() : 40)
                .build();

        AnthropicApi.Builder apiBuilder = AnthropicApi.builder()
                .apiKey(creds.getApiKey());

        if (StringUtils.hasText(creds.getApiEndpoint())) {
            apiBuilder.baseUrl(creds.getApiEndpoint());
        }

        return AnthropicChatModel.builder()
                .anthropicApi(apiBuilder.build())
                .defaultOptions(options)
                .build();
    }

    private ChatModel createGeminiModel(LlmCredentialsResponse creds) {
        var options = GoogleGenAiChatOptions.builder()
                .model(creds.getModelId())
                .temperature(safeDouble(creds.getTemperature(), 0.7))
                .maxOutputTokens(creds.getMaxOutputTokens() != null ? creds.getMaxOutputTokens() : 65536)
                .build();

        Client.Builder clientBuilder = Client.builder()
                .apiKey(creds.getApiKey());

        if (StringUtils.hasText(creds.getApiEndpoint())) {
            HttpOptions httpOptions = HttpOptions.builder()
                    .baseUrl(creds.getApiEndpoint())
                    .build();
            clientBuilder.httpOptions(httpOptions);
            log.info("Google GenAI 使用自定义端点: {}", creds.getApiEndpoint());
        }

        return GoogleGenAiChatModel.builder()
                .genAiClient(clientBuilder.build())
                .defaultOptions(options)
                .build();
    }

    /**
     * 以最小成本解析 LLM Provider 对应的 modelId（如 "gpt-4o-mini"、"gemini-2.5-flash"）。
     * <p>走 5 分钟 Caffeine 凭证缓存，热路径几乎零成本；解析失败回退到 providerId 字面值。
     * <p>用于 Token 真实计数、日志脱敏展示等需要"模型名"而非"provider 引用"的场景。
     */
    public String resolveModelName(String llmProviderId) {
        if (llmProviderId == null || llmProviderId.isBlank()) {
            return null;
        }
        try {
            LlmCredentialsResponse creds = getOrFetchCredentials(llmProviderId);
            if (creds != null && creds.getModelId() != null && !creds.getModelId().isBlank()) {
                return creds.getModelId();
            }
        } catch (Exception e) {
            log.debug("resolveModelName 回退到 providerId={} (原因: {})", llmProviderId, e.getMessage());
        }
        return llmProviderId;
    }

    private LlmCredentialsResponse getOrFetchCredentials(String llmProviderId) {
        return credentialsCache.get(llmProviderId, this::fetchCredentials);
    }

    private LlmCredentialsResponse fetchCredentials(String llmProviderId) {
        Result<LlmCredentialsResponse> result = aiFeignClient.getLlmCredentials(llmProviderId);
        if (!result.isSuccess() || result.getData() == null) {
            throw new BusinessException("获取 LLM 凭证失败: " + llmProviderId +
                    (result.getMessage() != null ? " - " + result.getMessage() : ""));
        }
        return result.getData();
    }

    private Double safeDouble(java.math.BigDecimal value, double defaultValue) {
        return value != null ? value.doubleValue() : defaultValue;
    }

    @SuppressWarnings("unused")
    private String getExtraConfigString(LlmCredentialsResponse creds, String key, String defaultValue) {
        Map<String, Object> extra = creds.getExtraConfig();
        if (extra != null && extra.containsKey(key)) {
            Object value = extra.get(key);
            return value != null ? value.toString() : defaultValue;
        }
        return defaultValue;
    }
}
