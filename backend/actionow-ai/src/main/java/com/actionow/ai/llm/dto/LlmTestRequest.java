package com.actionow.ai.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * LLM 测试请求 DTO
 *
 * @author Actionow
 */
@Data
@Schema(description = "LLM 测试请求")
public class LlmTestRequest {

    /**
     * 测试消息（可选）
     * 默认使用 "Hello, please respond with 'OK' if you can receive this message."
     */
    @Schema(description = "测试消息（可选）", example = "Hello")
    private String testMessage;

    /**
     * 超时时间（毫秒，可选）
     * 默认 30000ms (30秒)
     */
    @Schema(description = "超时时间（毫秒）", example = "30000")
    private Integer timeout;
}
