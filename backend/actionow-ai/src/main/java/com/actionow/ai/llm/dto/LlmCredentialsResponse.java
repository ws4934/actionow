package com.actionow.ai.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * LLM 凭证响应 DTO
 * 包含解析后的 API Key，供 Agent 模块创建 LLM 客户端
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmCredentialsResponse {

    /**
     * LLM Provider ID
     */
    private String id;

    /**
     * 模型厂商: GOOGLE, OPENAI, ANTHROPIC
     */
    private String provider;

    /**
     * 模型 ID
     * 如: gemini-2.0-flash, gpt-4o, claude-sonnet-4
     */
    private String modelId;

    /**
     * 模型显示名称
     */
    private String modelName;

    /**
     * 解析后的实际 API Key
     */
    private String apiKey;

    /**
     * API 端点（自定义，可选）
     * 仅包含 base URL，如: https://ark.cn-beijing.volces.com/api
     */
    private String apiEndpoint;

    /**
     * 自定义 completions 路径（可选）
     * 不同厂商使用不同路径:
     * - OpenAI: /v1/chat/completions (默认)
     * - 豆包: /v3/chat/completions
     * - 智谱: /paas/v4/chat/completions
     * - 通义: /compatible-mode/v1/chat/completions
     * 如果为空，则使用 Spring AI 默认路径
     */
    private String completionsPath;

    /**
     * 温度参数
     */
    private BigDecimal temperature;

    /**
     * 最大输出 token 数
     */
    private Integer maxOutputTokens;

    /**
     * Top P 参数
     */
    private BigDecimal topP;

    /**
     * Top K 参数
     */
    private Integer topK;

    /**
     * 上下文窗口大小
     */
    private Integer contextWindow;

    /**
     * 最大输入 token 数
     */
    private Integer maxInputTokens;

    /**
     * 额外配置参数
     * 可包含 organizationId, projectId 等
     */
    private Map<String, Object> extraConfig;
}
