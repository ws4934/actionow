package com.actionow.ai.llm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * LLM Provider 全局配置实体
 * 定义可用的 LLM 模型，与 Agent 解耦
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_llm_provider", autoResultMap = true)
public class LlmProvider extends BaseEntity {

    /**
     * 模型厂商
     * GOOGLE, OPENAI, ANTHROPIC, VOLCENGINE, ZHIPU, MOONSHOT, BAIDU, ALIBABA
     */
    private String provider;

    /**
     * 模型ID
     * 如: gemini-3-flash-preview, gpt-4o, claude-sonnet-4
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
     * API 端点（可选覆盖）
     */
    private String apiEndpoint;

    /**
     * API 端点引用
     * 引用 t_system_config.config_key，运行时解析 API Endpoint
     */
    private String apiEndpointRef;

    /**
     * 自定义 completions 路径
     * 不同厂商使用不同路径:
     * - OpenAI: /v1/chat/completions (默认)
     * - 豆包: /v3/chat/completions
     * - 智谱: /paas/v4/chat/completions
     * - 通义: /compatible-mode/v1/chat/completions
     */
    private String completionsPath;

    /**
     * API 密钥引用
     * 引用系统配置中的 API 密钥名称，如 'GOOGLE_API_KEY'
     */
    private String apiKeyRef;

    /**
     * 额外配置参数
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
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
     * 优先级（多模型选择时使用）
     */
    private Integer priority;

    /**
     * 描述
     */
    private String description;
}
