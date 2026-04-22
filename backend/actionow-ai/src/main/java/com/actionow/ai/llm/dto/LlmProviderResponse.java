package com.actionow.ai.llm.dto;

import com.actionow.ai.llm.entity.LlmProvider;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM Provider 响应 DTO
 *
 * @author Actionow
 */
@Data
public class LlmProviderResponse {

    private String id;

    /**
     * 模型厂商
     */
    private String provider;

    /**
     * 模型ID
     */
    private String modelId;

    /**
     * 模型显示名称
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
     * 自定义 completions 路径
     */
    private String completionsPath;

    /**
     * API 密钥引用
     */
    private String apiKeyRef;

    /**
     * API 端点引用
     */
    private String apiEndpointRef;

    /**
     * 额外配置参数
     */
    private Map<String, Object> extraConfig;

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
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换
     */
    public static LlmProviderResponse fromEntity(LlmProvider entity) {
        if (entity == null) {
            return null;
        }
        LlmProviderResponse response = new LlmProviderResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setModelId(entity.getModelId());
        response.setModelName(entity.getModelName());
        response.setTemperature(entity.getTemperature());
        response.setMaxOutputTokens(entity.getMaxOutputTokens());
        response.setTopP(entity.getTopP());
        response.setTopK(entity.getTopK());
        response.setApiEndpoint(entity.getApiEndpoint());
        response.setCompletionsPath(entity.getCompletionsPath());
        response.setApiKeyRef(entity.getApiKeyRef());
        response.setApiEndpointRef(entity.getApiEndpointRef());
        response.setExtraConfig(entity.getExtraConfig());
        response.setContextWindow(entity.getContextWindow());
        response.setMaxInputTokens(entity.getMaxInputTokens());
        response.setEnabled(entity.getEnabled());
        response.setPriority(entity.getPriority());
        response.setDescription(entity.getDescription());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
