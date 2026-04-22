package com.actionow.ai.plugin.groovy.binding;

import com.actionow.ai.llm.dto.LlmCredentialsResponse;
import com.actionow.ai.llm.service.LlmProviderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;

/**
 * LLM 调用绑定
 * 让 Groovy 脚本能直接调用 LLM，支持多厂商（Gemini/OpenAI-compatible/Anthropic）
 *
 * <p>Groovy 脚本中使用示例:
 * <pre>
 * // 简单对话
 * def result = llm.chat("分析这个角色的性格特点")
 *
 * // 带选项对话
 * def result = llm.chat("生成剧本大纲", [temperature: 0.7, maxTokens: 2048])
 *
 * // 结构化输出
 * def result = llm.chatStructured("生成分镜数据")
 *
 * // 多轮对话
 * def result = llm.chatMessages([
 *     [role: "user", content: "你好"],
 *     [role: "assistant", content: "你好！有什么可以帮助你的？"],
 *     [role: "user", content: "帮我分析一下这个场景"]
 * ])
 * </pre>
 *
 * @author Actionow
 */
@Slf4j
public class LlmBinding {

    private static final int DEFAULT_TIMEOUT_MS = 60000;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final LlmProviderService llmProviderService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    // 上下文信息
    private String workspaceId;
    private String userId;
    private String tenantSchema;

    // 默认配置（从 ModelProvider 注入）
    private String defaultLlmProviderId;
    private String defaultSystemPrompt;
    private Map<String, Object> defaultResponseSchema;

    public LlmBinding(LlmProviderService llmProviderService,
                      ObjectMapper objectMapper,
                      WebClient.Builder webClientBuilder) {
        this.llmProviderService = llmProviderService;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * 设置执行上下文
     */
    public void setContext(String workspaceId, String userId, String tenantSchema) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.tenantSchema = tenantSchema;
    }

    /**
     * 设置默认配置（从 ModelProvider 的 TEXT 专属字段注入）
     */
    public void setDefaults(String defaultLlmProviderId,
                            String defaultSystemPrompt,
                            Map<String, Object> defaultResponseSchema) {
        this.defaultLlmProviderId = defaultLlmProviderId;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.defaultResponseSchema = defaultResponseSchema;
    }

    // ==================== Public API ====================

    /**
     * 简单对话
     *
     * @param prompt 用户提示词
     * @return 结果 Map
     */
    public Map<String, Object> chat(String prompt) {
        return chat(prompt, Collections.emptyMap());
    }

    /**
     * 带选项对话
     *
     * @param prompt  用户提示词
     * @param options 选项 (llmProviderId, systemPrompt, temperature, maxTokens, topP, timeout)
     * @return 结果 Map
     */
    public Map<String, Object> chat(String prompt, Map<String, Object> options) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);
        return doChat(messages, options, false);
    }

    /**
     * 多轮对话
     *
     * @param messages 消息列表 [[role: "user", content: "..."], ...]
     * @return 结果 Map
     */
    public Map<String, Object> chatMessages(List<Map<String, Object>> messages) {
        return chatMessages(messages, Collections.emptyMap());
    }

    /**
     * 多轮对话（带选项）
     *
     * @param messages 消息列表
     * @param options  选项
     * @return 结果 Map
     */
    public Map<String, Object> chatMessages(List<Map<String, Object>> messages, Map<String, Object> options) {
        return doChat(messages, options, false);
    }

    /**
     * 结构化输出（使用默认 Schema）
     *
     * @param prompt 用户提示词
     * @return 结果 Map（data 字段为解析后的 JSON 对象）
     */
    public Map<String, Object> chatStructured(String prompt) {
        return chatStructured(prompt, null);
    }

    /**
     * 结构化输出（指定 Schema）
     *
     * @param prompt 用户提示词
     * @param schema JSON Schema
     * @return 结果 Map
     */
    public Map<String, Object> chatStructured(String prompt, Map<String, Object> schema) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        Map<String, Object> options = new HashMap<>();
        if (schema != null) {
            options.put("responseSchema", schema);
        }
        return doChat(messages, options, true);
    }

    /**
     * 多轮结构化输出
     *
     * @param messages 消息列表
     * @param schema   JSON Schema
     * @return 结果 Map
     */
    public Map<String, Object> chatStructured(List<Map<String, Object>> messages, Map<String, Object> schema) {
        Map<String, Object> options = new HashMap<>();
        if (schema != null) {
            options.put("responseSchema", schema);
        }
        return doChat(messages, options, true);
    }

    /**
     * 列出可用的 LLM Provider
     *
     * @return Provider 信息列表
     */
    public List<Map<String, Object>> listProviders() {
        try {
            return llmProviderService.findAllEnabled().stream()
                    .map(p -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", p.getId());
                        info.put("provider", p.getProvider());
                        info.put("modelId", p.getModelId());
                        info.put("modelName", p.getModelName());
                        return info;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("[LlmBinding] Failed to list providers", e);
            return Collections.emptyList();
        }
    }

    // ==================== Core Implementation ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> doChat(List<Map<String, Object>> messages,
                                       Map<String, Object> options,
                                       boolean structured) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 解析选项
            String llmProviderId = resolveString(options, "llmProviderId", defaultLlmProviderId);
            String systemPrompt = resolveString(options, "systemPrompt", defaultSystemPrompt);
            Double temperature = resolveDouble(options, "temperature", null);
            Integer maxTokens = resolveInteger(options, "maxTokens", DEFAULT_MAX_TOKENS);
            Double topP = resolveDouble(options, "topP", null);
            int timeout = resolveInteger(options, "timeout", DEFAULT_TIMEOUT_MS);

            Map<String, Object> responseSchema = null;
            if (structured) {
                responseSchema = (Map<String, Object>) options.getOrDefault("responseSchema", defaultResponseSchema);
            }

            if (!StringUtils.hasText(llmProviderId)) {
                return errorResult("CONFIG_ERROR", "未配置 LLM Provider ID（llmProviderId 或 defaultLlmProviderId 均为空）", false);
            }

            // 2. 获取凭证
            LlmCredentialsResponse credentials = llmProviderService.getCredentials(llmProviderId)
                    .orElse(null);
            if (credentials == null) {
                return errorResult("CREDENTIALS_ERROR", "LLM Provider 不存在或已禁用: " + llmProviderId, false);
            }

            if (!StringUtils.hasText(credentials.getApiKey())) {
                return errorResult("CREDENTIALS_ERROR", "LLM Provider API Key 未配置: " + llmProviderId, false);
            }

            // 使用凭证中的默认值作为后备
            if (temperature == null && credentials.getTemperature() != null) {
                temperature = credentials.getTemperature().doubleValue();
            }
            if (credentials.getMaxOutputTokens() != null && maxTokens == DEFAULT_MAX_TOKENS) {
                maxTokens = credentials.getMaxOutputTokens();
            }
            if (topP == null && credentials.getTopP() != null) {
                topP = credentials.getTopP().doubleValue();
            }

            // 3. 根据 provider 构建请求并调用
            String provider = credentials.getProvider().toUpperCase();
            String responseBody;

            switch (provider) {
                case "GOOGLE" -> responseBody = callGemini(credentials, messages, systemPrompt,
                        temperature, maxTokens, topP, responseSchema, timeout);
                case "ANTHROPIC" -> responseBody = callAnthropic(credentials, messages, systemPrompt,
                        temperature, maxTokens, topP, responseSchema, timeout);
                default -> responseBody = callOpenAiCompatible(credentials, messages, systemPrompt,
                        temperature, maxTokens, topP, responseSchema, timeout);
            }

            // 4. 解析响应
            long responseTimeMs = System.currentTimeMillis() - startTime;
            return parseResponse(provider, responseBody, responseSchema != null, responseTimeMs,
                    credentials.getModelId(), credentials.getProvider());

        } catch (WebClientResponseException e) {
            long responseTimeMs = System.currentTimeMillis() - startTime;
            String safeBody = maskSensitive(truncate(e.getResponseBodyAsString(), 500));
            log.error("[LlmBinding] API error: status={}, body={}", e.getStatusCode(), safeBody);
            boolean retryable = e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError();
            return errorResult("API_ERROR",
                    "HTTP " + e.getStatusCode().value() + ": " + safeBody,
                    retryable, responseTimeMs);
        } catch (Exception e) {
            long responseTimeMs = System.currentTimeMillis() - startTime;
            log.error("[LlmBinding] Unexpected error: {}", maskSensitive(e.getMessage()));
            return errorResult("INTERNAL_ERROR", maskSensitive(e.getMessage()), false, responseTimeMs);
        }
    }

    // ==================== Provider-specific request builders ====================

    /**
     * 调用 OpenAI-compatible API (OpenAI, VolcEngine, DeepSeek, Zhipu, Moonshot, Alibaba 等)
     */
    private String callOpenAiCompatible(LlmCredentialsResponse credentials,
                                        List<Map<String, Object>> messages,
                                        String systemPrompt,
                                        Double temperature, Integer maxTokens, Double topP,
                                        Map<String, Object> responseSchema,
                                        int timeout) {
        String apiEndpoint = resolveOpenAiEndpoint(credentials, credentials.getProvider());

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        if (maxTokens != null) {
            requestBody.put("max_tokens", maxTokens);
        }
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }
        if (topP != null) {
            requestBody.put("top_p", topP);
        }

        // 构建 messages（系统提示词作为第一条 message）
        ArrayNode messagesNode = requestBody.putArray("messages");
        if (StringUtils.hasText(systemPrompt)) {
            ObjectNode sysMsg = messagesNode.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
        }
        for (Map<String, Object> msg : messages) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", (String) msg.get("role"));
            msgNode.put("content", (String) msg.get("content"));
        }

        // 结构化输出
        if (responseSchema != null && !responseSchema.isEmpty()) {
            ObjectNode responseFormat = requestBody.putObject("response_format");
            responseFormat.put("type", "json_schema");
            ObjectNode jsonSchema = responseFormat.putObject("json_schema");
            jsonSchema.put("name", "response");
            jsonSchema.set("schema", objectMapper.valueToTree(responseSchema));
            jsonSchema.put("strict", true);
        }

        return executeRequest(apiEndpoint, requestBody, "Bearer " + credentials.getApiKey(), null, timeout);
    }

    /**
     * 调用 Gemini API
     */
    private String callGemini(LlmCredentialsResponse credentials,
                              List<Map<String, Object>> messages,
                              String systemPrompt,
                              Double temperature, Integer maxTokens, Double topP,
                              Map<String, Object> responseSchema,
                              int timeout) {
        // 构建端点
        Map<String, Object> extraConfig = credentials.getExtraConfig();
        boolean isVertexAI = extraConfig != null && extraConfig.containsKey("projectId");

        String apiEndpoint;
        if (isVertexAI) {
            String projectId = (String) extraConfig.get("projectId");
            String location = extraConfig.containsKey("location")
                    ? (String) extraConfig.get("location") : "us-central1";
            apiEndpoint = String.format(
                    "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                    location, projectId, location, credentials.getModelId());
        } else {
            String baseUrl = StringUtils.hasText(credentials.getApiEndpoint())
                    ? credentials.getApiEndpoint()
                    : "https://generativelanguage.googleapis.com";
            apiEndpoint = baseUrl + String.format("/v1beta/models/%s:generateContent",
                    credentials.getModelId());
        }

        // 构建请求体
        ObjectNode requestBody = objectMapper.createObjectNode();

        // 系统提示词
        if (StringUtils.hasText(systemPrompt)) {
            ObjectNode systemInstruction = requestBody.putObject("systemInstruction");
            ArrayNode parts = systemInstruction.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", systemPrompt);
        }

        // 消息内容
        ArrayNode contents = requestBody.putArray("contents");
        for (Map<String, Object> msg : messages) {
            ObjectNode content = contents.addObject();
            String role = (String) msg.get("role");
            // Gemini 使用 "user" 和 "model" 角色
            content.put("role", "assistant".equals(role) ? "model" : role);
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", (String) msg.get("content"));
        }

        // 生成配置
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        if (maxTokens != null) {
            generationConfig.put("maxOutputTokens", maxTokens);
        }
        if (temperature != null) {
            generationConfig.put("temperature", temperature);
        }
        if (topP != null) {
            generationConfig.put("topP", topP);
        }

        // 结构化输出
        if (responseSchema != null && !responseSchema.isEmpty()) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.set("responseSchema", objectMapper.valueToTree(responseSchema));
        }

        // 认证：Vertex AI 用 Bearer token，标准 Gemini API 用 x-goog-api-key header
        if (isVertexAI) {
            return executeRequest(apiEndpoint, requestBody, "Bearer " + credentials.getApiKey(), null, timeout);
        } else {
            Map<String, String> extraHeaders = Map.of("x-goog-api-key", credentials.getApiKey());
            return executeRequest(apiEndpoint, requestBody, null, extraHeaders, timeout);
        }
    }

    /**
     * 调用 Anthropic API
     */
    private String callAnthropic(LlmCredentialsResponse credentials,
                                 List<Map<String, Object>> messages,
                                 String systemPrompt,
                                 Double temperature, Integer maxTokens, Double topP,
                                 Map<String, Object> responseSchema,
                                 int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://api.anthropic.com/v1/messages";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        if (maxTokens != null) {
            requestBody.put("max_tokens", maxTokens);
        }
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }
        if (topP != null) {
            requestBody.put("top_p", topP);
        }

        // Anthropic 系统提示词：使用顶层 system 字段
        // 如果有结构化输出，追加 JSON Schema 引导
        String effectiveSystemPrompt = systemPrompt;
        if (responseSchema != null && !responseSchema.isEmpty()) {
            String schemaInstruction;
            try {
                schemaInstruction = "\n\nYou must respond with a valid JSON object that conforms to the following JSON Schema:\n"
                        + objectMapper.writeValueAsString(responseSchema)
                        + "\n\nRespond ONLY with the JSON object, no other text.";
            } catch (Exception e) {
                schemaInstruction = "\n\nRespond with a valid JSON object.";
            }
            effectiveSystemPrompt = (StringUtils.hasText(systemPrompt) ? systemPrompt : "") + schemaInstruction;
        }
        if (StringUtils.hasText(effectiveSystemPrompt)) {
            requestBody.put("system", effectiveSystemPrompt);
        }

        // 消息
        ArrayNode messagesNode = requestBody.putArray("messages");
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            // Anthropic 不支持 system role in messages
            if ("system".equals(role)) continue;
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", role);
            msgNode.put("content", (String) msg.get("content"));
        }

        Map<String, String> customHeaders = Map.of(
                "x-api-key", credentials.getApiKey(),
                "anthropic-version", "2023-06-01"
        );

        return executeRequest(apiEndpoint, requestBody, null, customHeaders, timeout);
    }

    // ==================== HTTP Execution ====================

    private String executeRequest(String apiEndpoint, ObjectNode requestBody,
                                  String authHeader, Map<String, String> customHeaders,
                                  int timeout) {
        WebClient client = webClientBuilder.build();

        WebClient.RequestBodySpec request = client.post()
                .uri(apiEndpoint)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(authHeader)) {
            request.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (customHeaders != null) {
            customHeaders.forEach(request::header);
        }

        return request
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .block();
    }

    // ==================== Response Parsing ====================

    private Map<String, Object> parseResponse(String provider, String responseBody,
                                              boolean structured, long responseTimeMs,
                                              String modelId, String providerName) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String content;
            Map<String, Object> usage = new HashMap<>();

            switch (provider) {
                case "GOOGLE" -> {
                    content = root.path("candidates").path(0)
                            .path("content").path("parts").path(0).path("text").asText("");
                    usage.put("promptTokens", root.path("usageMetadata").path("promptTokenCount").asInt(0));
                    usage.put("completionTokens", root.path("usageMetadata").path("candidatesTokenCount").asInt(0));
                }
                case "ANTHROPIC" -> {
                    content = root.path("content").path(0).path("text").asText("");
                    usage.put("promptTokens", root.path("usage").path("input_tokens").asInt(0));
                    usage.put("completionTokens", root.path("usage").path("output_tokens").asInt(0));
                }
                default -> {
                    // OpenAI-compatible
                    content = root.path("choices").path(0).path("message").path("content").asText("");
                    usage.put("promptTokens", root.path("usage").path("prompt_tokens").asInt(0));
                    usage.put("completionTokens", root.path("usage").path("completion_tokens").asInt(0));
                }
            }

            int totalTokens = ((Number) usage.getOrDefault("promptTokens", 0)).intValue()
                    + ((Number) usage.getOrDefault("completionTokens", 0)).intValue();
            usage.put("totalTokens", totalTokens);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("content", content);
            result.put("usage", usage);
            result.put("model", modelId);
            result.put("provider", providerName);
            result.put("responseTimeMs", responseTimeMs);

            // 结构化输出：尝试解析 content 为 JSON
            if (structured && StringUtils.hasText(content)) {
                try {
                    Map<String, Object> data = objectMapper.readValue(content,
                            new TypeReference<Map<String, Object>>() {});
                    result.put("data", data);
                } catch (Exception e) {
                    log.warn("[LlmBinding] Failed to parse structured response as JSON, returning raw content");
                    result.put("data", null);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("[LlmBinding] Failed to parse response: {}", responseBody, e);
            return errorResult("PARSE_ERROR", "响应解析失败: " + e.getMessage(), false, responseTimeMs);
        }
    }

    // ==================== Helpers ====================

    private static final Map<String, String> PROVIDER_DEFAULT_ENDPOINTS = Map.of(
            "VOLCENGINE", "https://ark.cn-beijing.volces.com/api",
            "ZHIPU", "https://open.bigmodel.cn",
            "MOONSHOT", "https://api.moonshot.cn",
            "DEEPSEEK", "https://api.deepseek.com",
            "ALIBABA", "https://dashscope.aliyuncs.com"
    );

    private String resolveOpenAiEndpoint(LlmCredentialsResponse credentials, String provider) {
        // 优先使用 completionsPath（支持豆包 /v3、智谱 /paas/v4 等自定义路径）
        if (StringUtils.hasText(credentials.getCompletionsPath())) {
            String base = StringUtils.hasText(credentials.getApiEndpoint())
                    ? credentials.getApiEndpoint()
                    : PROVIDER_DEFAULT_ENDPOINTS.getOrDefault(
                            provider != null ? provider.toUpperCase() : "", "https://api.openai.com");
            base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            String path = credentials.getCompletionsPath().startsWith("/")
                    ? credentials.getCompletionsPath() : "/" + credentials.getCompletionsPath();
            return base + path;
        }
        // 其次使用 apiEndpoint 作为完整 URL（与 LlmTestService 一致）
        if (StringUtils.hasText(credentials.getApiEndpoint())) {
            return credentials.getApiEndpoint();
        }
        return "https://api.openai.com/v1/chat/completions";
    }

    private String resolveString(Map<String, Object> options, String key, String defaultValue) {
        Object val = options != null ? options.get(key) : null;
        if (val instanceof String s && StringUtils.hasText(s)) {
            return s;
        }
        return defaultValue;
    }

    private Double resolveDouble(Map<String, Object> options, String key, Double defaultValue) {
        Object val = options != null ? options.get(key) : null;
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }

    private Integer resolveInteger(Map<String, Object> options, String key, Integer defaultValue) {
        Object val = options != null ? options.get(key) : null;
        if (val instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private Map<String, Object> errorResult(String code, String message, boolean retryable) {
        return errorResult(code, message, retryable, 0);
    }

    private Map<String, Object> errorResult(String code, String message, boolean retryable, long responseTimeMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        result.put("error", error);

        if (responseTimeMs > 0) {
            result.put("responseTimeMs", responseTimeMs);
        }

        return result;
    }

    // ==================== Security Helpers ====================

    /**
     * 遮掩敏感信息（API key 模式）
     */
    static String maskSensitive(String text) {
        if (text == null) return null;
        // OpenAI: sk-xxx, Gemini: AIza..., Anthropic: sk-ant-xxx, 通用长 hex/base64 token
        return text
                .replaceAll("sk-ant-[A-Za-z0-9_-]{10,}", "sk-ant-***")
                .replaceAll("sk-[A-Za-z0-9_-]{10,}", "sk-***")
                .replaceAll("AIza[A-Za-z0-9_-]{10,}", "AIza***")
                .replaceAll("Bearer [A-Za-z0-9._-]{20,}", "Bearer ***");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...(truncated)";
    }
}
