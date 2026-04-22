package com.actionow.ai.llm.service;

import com.actionow.ai.llm.dto.LlmCredentialsResponse;
import com.actionow.ai.llm.dto.LlmTestRequest;
import com.actionow.ai.llm.dto.LlmTestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * LLM 测试服务
 * 支持测试各厂商 LLM 的可用性
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmTestService {

    private static final String DEFAULT_TEST_MESSAGE = "Hello, please respond with 'OK' if you can receive this message.";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    /**
     * 测试 LLM 可用性
     *
     * @param credentials 凭证信息
     * @param request     测试请求
     * @return 测试结果
     */
    public LlmTestResponse testLlm(LlmCredentialsResponse credentials, LlmTestRequest request) {
        String provider = credentials.getProvider();
        String testMessage = request != null && StringUtils.hasText(request.getTestMessage())
                ? request.getTestMessage() : DEFAULT_TEST_MESSAGE;
        int timeout = request != null && request.getTimeout() != null
                ? request.getTimeout() : DEFAULT_TIMEOUT_MS;

        log.info("开始测试 LLM: provider={}, modelId={}, timeout={}ms",
                provider, credentials.getModelId(), timeout);

        try {
            return switch (provider.toUpperCase()) {
                case "OPENAI" -> testOpenAI(credentials, testMessage, timeout);
                case "ANTHROPIC" -> testAnthropic(credentials, testMessage, timeout);
                case "GOOGLE" -> testGoogle(credentials, testMessage, timeout);
                case "VOLCENGINE" -> testVolcEngine(credentials, testMessage, timeout);
                case "ZHIPU" -> testZhipu(credentials, testMessage, timeout);
                case "MOONSHOT" -> testMoonshot(credentials, testMessage, timeout);
                case "BAIDU" -> testBaidu(credentials, testMessage, timeout);
                case "ALIBABA", "DASHSCOPE" -> testAlibaba(credentials, testMessage, timeout);
                case "DEEPSEEK" -> testDeepSeek(credentials, testMessage, timeout);
                default -> LlmTestResponse.fail(
                        credentials.getId(), provider, credentials.getModelId(),
                        credentials.getModelName(), "UNSUPPORTED_PROVIDER",
                        "不支持的 LLM 厂商: " + provider, null);
            };
        } catch (Exception e) {
            log.error("测试 LLM 异常: provider={}, modelId={}", provider, credentials.getModelId(), e);
            return LlmTestResponse.fail(
                    credentials.getId(), provider, credentials.getModelId(),
                    credentials.getModelName(), "EXCEPTION",
                    "测试异常: " + e.getMessage(), null);
        }
    }

    /**
     * 测试 OpenAI
     */
    private LlmTestResponse testOpenAI(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://api.openai.com/v1/chat/completions";

        // 构建请求体
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        requestBody.put("max_tokens", 50);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTest(credentials, apiEndpoint, requestBody, timeout,
                "Bearer " + credentials.getApiKey(), this::parseOpenAIResponse);
    }

    /**
     * 测试 Anthropic
     */
    private LlmTestResponse testAnthropic(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://api.anthropic.com/v1/messages";

        // 构建请求体
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        requestBody.put("max_tokens", 50);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTestWithCustomHeaders(credentials, apiEndpoint, requestBody, timeout,
                Map.of(
                        "x-api-key", credentials.getApiKey(),
                        "anthropic-version", "2023-06-01"
                ), this::parseAnthropicResponse);
    }

    /**
     * 测试 Google (Gemini)
     */
    private LlmTestResponse testGoogle(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        // 检查是否是 Vertex AI
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
            // Google AI Studio
            String tmpBaseUrl = StringUtils.hasText(credentials.getApiEndpoint()) ?
                    credentials.getApiEndpoint() : "https://generativelanguage.googleapis.com";
            apiEndpoint = tmpBaseUrl + String.format("/v1beta/models/%s:generateContent?key=%s", credentials.getModelId(), credentials.getApiKey());
        }

        // 构建请求体
        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        ObjectNode part = parts.addObject();
        part.put("text", testMessage);

        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("maxOutputTokens", 50);

        if (isVertexAI) {
            return executeTest(credentials, apiEndpoint, requestBody, timeout,
                    "Bearer " + credentials.getApiKey(), this::parseGoogleResponse);
        } else {
            // Google AI Studio - API key in URL
            return executeTestNoAuth(credentials, apiEndpoint, requestBody, timeout,
                    this::parseGoogleResponse);
        }
    }

    /**
     * 测试火山引擎 (VolcEngine)
     */
    private LlmTestResponse testVolcEngine(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        requestBody.put("max_tokens", 50);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTest(credentials, apiEndpoint, requestBody, timeout,
                "Bearer " + credentials.getApiKey(), this::parseOpenAIResponse);
    }

    /**
     * 测试智谱 (Zhipu)
     */
    private LlmTestResponse testZhipu(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://open.bigmodel.cn/api/paas/v4/chat/completions";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        requestBody.put("max_tokens", 50);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTest(credentials, apiEndpoint, requestBody, timeout,
                "Bearer " + credentials.getApiKey(), this::parseOpenAIResponse);
    }

    /**
     * 测试 Moonshot (月之暗面)
     */
    private LlmTestResponse testMoonshot(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://api.moonshot.cn/v1/chat/completions";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        requestBody.put("max_tokens", 50);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTest(credentials, apiEndpoint, requestBody, timeout,
                "Bearer " + credentials.getApiKey(), this::parseOpenAIResponse);
    }

    /**
     * 测试百度 (文心)
     */
    private LlmTestResponse testBaidu(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        // 百度需要特殊的 access_token 获取流程，这里简化处理
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : String.format("https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/%s?access_token=%s",
                credentials.getModelId(), credentials.getApiKey());

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTestNoAuth(credentials, apiEndpoint, requestBody, timeout,
                this::parseBaiduResponse);
    }

    /**
     * 测试阿里云 (通义千问)
     */
    private LlmTestResponse testAlibaba(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        requestBody.put("max_tokens", 50);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTest(credentials, apiEndpoint, requestBody, timeout,
                "Bearer " + credentials.getApiKey(), this::parseOpenAIResponse);
    }

    /**
     * 测试 DeepSeek
     */
    private LlmTestResponse testDeepSeek(LlmCredentialsResponse credentials, String testMessage, int timeout) {
        String apiEndpoint = StringUtils.hasText(credentials.getApiEndpoint())
                ? credentials.getApiEndpoint()
                : "https://api.deepseek.com/v1/chat/completions";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", credentials.getModelId());
        requestBody.put("max_tokens", 50);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", testMessage);

        return executeTest(credentials, apiEndpoint, requestBody, timeout,
                "Bearer " + credentials.getApiKey(), this::parseOpenAIResponse);
    }

    // ==================== 执行测试的通用方法 ====================

    /**
     * 执行测试（Bearer 认证）
     */
    private LlmTestResponse executeTest(LlmCredentialsResponse credentials, String apiEndpoint,
                                         ObjectNode requestBody, int timeout, String authHeader,
                                         ResponseParser parser) {
        if (!StringUtils.hasText(credentials.getApiKey())) {
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "NO_API_KEY",
                    "API Key 未配置或获取失败", apiEndpoint);
        }

        WebClient client = webClientBuilder.build();
        long startTime = System.currentTimeMillis();

        try {
            String response = client.post()
                    .uri(apiEndpoint)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            long responseTime = System.currentTimeMillis() - startTime;
            return parser.parse(credentials, apiEndpoint, response, responseTime);

        } catch (WebClientResponseException e) {
            log.error("LLM API 调用失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "HTTP_" + e.getStatusCode().value(),
                    "API 调用失败: " + e.getResponseBodyAsString(), apiEndpoint);
        } catch (Exception e) {
            log.error("LLM 测试异常", e);
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "EXCEPTION",
                    e.getMessage(), apiEndpoint);
        }
    }

    /**
     * 执行测试（自定义 Headers）
     */
    private LlmTestResponse executeTestWithCustomHeaders(LlmCredentialsResponse credentials, String apiEndpoint,
                                                          ObjectNode requestBody, int timeout,
                                                          Map<String, String> headers, ResponseParser parser) {
        if (!StringUtils.hasText(credentials.getApiKey())) {
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "NO_API_KEY",
                    "API Key 未配置或获取失败", apiEndpoint);
        }

        WebClient client = webClientBuilder.build();
        long startTime = System.currentTimeMillis();

        try {
            WebClient.RequestBodySpec request = client.post()
                    .uri(apiEndpoint)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.header(header.getKey(), header.getValue());
            }

            String response = request
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            long responseTime = System.currentTimeMillis() - startTime;
            return parser.parse(credentials, apiEndpoint, response, responseTime);

        } catch (WebClientResponseException e) {
            log.error("LLM API 调用失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "HTTP_" + e.getStatusCode().value(),
                    "API 调用失败: " + e.getResponseBodyAsString(), apiEndpoint);
        } catch (Exception e) {
            log.error("LLM 测试异常", e);
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "EXCEPTION",
                    e.getMessage(), apiEndpoint);
        }
    }

    /**
     * 执行测试（无认证，API key 在 URL 中）
     */
    private LlmTestResponse executeTestNoAuth(LlmCredentialsResponse credentials, String apiEndpoint,
                                               ObjectNode requestBody, int timeout, ResponseParser parser) {
        WebClient client = webClientBuilder.build();
        long startTime = System.currentTimeMillis();

        try {
            String response = client.post()
                    .uri(apiEndpoint)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            long responseTime = System.currentTimeMillis() - startTime;
            return parser.parse(credentials, apiEndpoint, response, responseTime);

        } catch (WebClientResponseException e) {
            log.error("LLM API 调用失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "HTTP_" + e.getStatusCode().value(),
                    "API 调用失败: " + e.getResponseBodyAsString(), apiEndpoint);
        } catch (Exception e) {
            log.error("LLM 测试异常", e);
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "EXCEPTION",
                    e.getMessage(), apiEndpoint);
        }
    }

    // ==================== 响应解析器 ====================

    @FunctionalInterface
    interface ResponseParser {
        LlmTestResponse parse(LlmCredentialsResponse credentials, String apiEndpoint,
                              String response, long responseTime);
    }

    /**
     * 检查响应是否为非 JSON 格式（HTML/XML），如果是则返回错误响应
     */
    private LlmTestResponse checkNonJsonResponse(LlmCredentialsResponse credentials, String apiEndpoint,
                                                  String response) {
        if (response != null && !response.isEmpty()) {
            String trimmed = response.trim();
            if (trimmed.startsWith("<")) {
                log.error("API 返回非 JSON 响应（HTML/XML），{}", trimmed);
                log.error(credentials.toString());
                return LlmTestResponse.fail(
                        credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                        credentials.getModelName(), "INVALID_RESPONSE",
                        "API 返回 HTML 而非 JSON，可能原因：模型名称无效、API Key 错误或端点地址不正确", apiEndpoint);
            }
        }
        return null;
    }

    /**
     * 解析 OpenAI 格式响应（OpenAI, VolcEngine, Zhipu, Moonshot, Alibaba）
     */
    private LlmTestResponse parseOpenAIResponse(LlmCredentialsResponse credentials, String apiEndpoint,
                                                 String response, long responseTime) {
        LlmTestResponse htmlCheck = checkNonJsonResponse(credentials, apiEndpoint, response);
        if (htmlCheck != null) return htmlCheck;
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            Integer tokensUsed = root.path("usage").path("total_tokens").asInt(0);

            return LlmTestResponse.success(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), responseTime, content,
                    tokensUsed > 0 ? tokensUsed : null, apiEndpoint);
        } catch (Exception e) {
            log.error("解析 OpenAI 响应失败", e);
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "PARSE_ERROR",
                    "响应解析失败: " + e.getMessage(), apiEndpoint);
        }
    }

    /**
     * 解析 Anthropic 响应
     */
    private LlmTestResponse parseAnthropicResponse(LlmCredentialsResponse credentials, String apiEndpoint,
                                                    String response, long responseTime) {
        LlmTestResponse htmlCheck = checkNonJsonResponse(credentials, apiEndpoint, response);
        if (htmlCheck != null) return htmlCheck;
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("content").path(0).path("text").asText("");
            Integer inputTokens = root.path("usage").path("input_tokens").asInt(0);
            Integer outputTokens = root.path("usage").path("output_tokens").asInt(0);
            Integer tokensUsed = inputTokens + outputTokens;

            return LlmTestResponse.success(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), responseTime, content,
                    tokensUsed > 0 ? tokensUsed : null, apiEndpoint);
        } catch (Exception e) {
            log.error("解析 Anthropic 响应失败", e);
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "PARSE_ERROR",
                    "响应解析失败: " + e.getMessage(), apiEndpoint);
        }
    }

    /**
     * 解析 Google (Gemini) 响应
     */
    private LlmTestResponse parseGoogleResponse(LlmCredentialsResponse credentials, String apiEndpoint,
                                                 String response, long responseTime) {
        LlmTestResponse htmlCheck = checkNonJsonResponse(credentials, apiEndpoint, response);
        if (htmlCheck != null) return htmlCheck;
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text").asText("");
            Integer promptTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            Integer outputTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);
            Integer tokensUsed = promptTokens + outputTokens;

            return LlmTestResponse.success(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), responseTime, content,
                    tokensUsed > 0 ? tokensUsed : null, apiEndpoint);
        } catch (Exception e) {
            log.error("解析 Google 响应失败", e);
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "PARSE_ERROR",
                    "响应解析失败: " + e.getMessage(), apiEndpoint);
        }
    }

    /**
     * 解析百度 (文心) 响应
     */
    private LlmTestResponse parseBaiduResponse(LlmCredentialsResponse credentials, String apiEndpoint,
                                                String response, long responseTime) {
        LlmTestResponse htmlCheck = checkNonJsonResponse(credentials, apiEndpoint, response);
        if (htmlCheck != null) return htmlCheck;
        try {
            JsonNode root = objectMapper.readTree(response);

            // 检查错误
            if (root.has("error_code")) {
                return LlmTestResponse.fail(
                        credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                        credentials.getModelName(), "BAIDU_" + root.path("error_code").asText(),
                        root.path("error_msg").asText("未知错误"), apiEndpoint);
            }

            String content = root.path("result").asText("");
            Integer tokensUsed = root.path("usage").path("total_tokens").asInt(0);

            return LlmTestResponse.success(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), responseTime, content,
                    tokensUsed > 0 ? tokensUsed : null, apiEndpoint);
        } catch (Exception e) {
            log.error("解析百度响应失败", e);
            return LlmTestResponse.fail(
                    credentials.getId(), credentials.getProvider(), credentials.getModelId(),
                    credentials.getModelName(), "PARSE_ERROR",
                    "响应解析失败: " + e.getMessage(), apiEndpoint);
        }
    }
}
