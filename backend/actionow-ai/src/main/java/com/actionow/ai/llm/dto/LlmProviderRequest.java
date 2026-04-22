package com.actionow.ai.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * LLM Provider 请求 DTO
 *
 * @author Actionow
 */
@Data
public class LlmProviderRequest {

    /**
     * 模型厂商
     */
    @NotBlank(message = "厂商不能为空")
    @Size(max = 50, message = "厂商长度不能超过50")
    private String provider;

    /**
     * 模型ID
     */
    @NotBlank(message = "模型ID不能为空")
    @Size(max = 100, message = "模型ID长度不能超过100")
    private String modelId;

    /**
     * 模型显示名称
     */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 200, message = "模型名称长度不能超过200")
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
    @Size(max = 500, message = "API端点长度不能超过500")
    private String apiEndpoint;

    /**
     * 自定义 completions 路径
     * 如: /v3/chat/completions (豆包), /paas/v4/chat/completions (智谱)
     */
    @Size(max = 255, message = "completions路径长度不能超过255")
    private String completionsPath;

    /**
     * API 密钥引用
     */
    @Size(max = 100, message = "API密钥引用长度不能超过100")
    private String apiKeyRef;

    /**
     * API 端点引用
     * 引用 t_system_config.config_key，运行时解析 API Endpoint
     */
    @Size(max = 100, message = "API端点引用长度不能超过100")
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
}
