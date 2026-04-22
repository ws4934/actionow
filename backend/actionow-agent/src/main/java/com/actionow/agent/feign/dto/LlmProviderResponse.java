package com.actionow.agent.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * LLM Provider 响应 DTO
 * 用于 Feign 客户端从 actionow-ai 获取 LLM 配置
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmProviderResponse {

    /**
     * ID
     */
    private String id;

    /**
     * 提供商: GOOGLE, OPENAI, ANTHROPIC, VOLCENGINE, ZHIPU, MOONSHOT, BAIDU, ALIBABA
     */
    private String provider;

    /**
     * 模型 ID
     */
    private String modelId;

    /**
     * 模型名称
     */
    private String modelName;

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
     * API 端点
     */
    private String apiEndpoint;

    /**
     * 上下文窗口大小
     */
    private Integer contextWindow;

    /**
     * 最大输入 token 数
     */
    private Integer maxInputTokens;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 描述
     */
    private String description;

    /**
     * 生成 Google ADK 模型字符串
     * 格式: provider/modelId 或直接 modelId
     */
    public String toAdkModelString() {
        if ("GOOGLE".equalsIgnoreCase(provider)) {
            return modelId;
        }
        return provider.toLowerCase() + "/" + modelId;
    }
}
