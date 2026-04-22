package com.actionow.ai.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * LLM 测试响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@Schema(description = "LLM 测试响应")
public class LlmTestResponse {

    /**
     * 测试是否成功
     */
    @Schema(description = "是否成功")
    private Boolean success;

    /**
     * Provider ID
     */
    @Schema(description = "Provider ID")
    private String providerId;

    /**
     * 厂商
     */
    @Schema(description = "厂商")
    private String provider;

    /**
     * 模型 ID
     */
    @Schema(description = "模型 ID")
    private String modelId;

    /**
     * 模型名称
     */
    @Schema(description = "模型名称")
    private String modelName;

    /**
     * 响应时间（毫秒）
     */
    @Schema(description = "响应时间（毫秒）")
    private Long responseTimeMs;

    /**
     * 模型响应内容（测试消息的回复）
     */
    @Schema(description = "模型响应内容")
    private String responseContent;

    /**
     * 使用的 Token 数（如果可获取）
     */
    @Schema(description = "使用的 Token 数")
    private Integer tokensUsed;

    /**
     * 错误码（失败时）
     */
    @Schema(description = "错误码")
    private String errorCode;

    /**
     * 错误信息（失败时）
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * API 端点（用于诊断）
     */
    @Schema(description = "API 端点")
    private String apiEndpoint;

    /**
     * 测试时间戳
     */
    @Schema(description = "测试时间戳")
    private Long timestamp;

    /**
     * 创建成功响应
     */
    public static LlmTestResponse success(String providerId, String provider, String modelId,
                                          String modelName, long responseTimeMs,
                                          String responseContent, Integer tokensUsed, String apiEndpoint) {
        return LlmTestResponse.builder()
                .success(true)
                .providerId(providerId)
                .provider(provider)
                .modelId(modelId)
                .modelName(modelName)
                .responseTimeMs(responseTimeMs)
                .responseContent(responseContent)
                .tokensUsed(tokensUsed)
                .apiEndpoint(apiEndpoint)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建失败响应
     */
    public static LlmTestResponse fail(String providerId, String provider, String modelId,
                                       String modelName, String errorCode, String errorMessage,
                                       String apiEndpoint) {
        return LlmTestResponse.builder()
                .success(false)
                .providerId(providerId)
                .provider(provider)
                .modelId(modelId)
                .modelName(modelName)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .apiEndpoint(apiEndpoint)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
