package com.actionow.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 配置
 * 使用 Gemini REST API（generativelanguage.googleapis.com）
 *
 * @author Actionow
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "actionow.embedding")
public class EmbeddingConfig {

    /**
     * Google AI API Key
     */
    private String apiKey;

    /**
     * Gemini API 基础 URL
     */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /**
     * Embedding 模型名称
     */
    private String model = "text-embedding-004";

    /**
     * Embedding 维度
     */
    private int dimension = 768;

    /**
     * 批处理大小
     */
    private int batchSize = 100;
}
