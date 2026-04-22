package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 响应元数据 DTO
 * 包含执行过程中的元信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseMetadata {

    /**
     * 外部任务 ID（第三方服务返回，用于轮询/回调）
     */
    private String externalTaskId;

    /**
     * 外部运行 ID
     */
    private String externalRunId;

    /**
     * 执行耗时（毫秒）
     */
    private Long elapsedMs;

    /**
     * Token 总消耗（LLM 适用）
     */
    private Integer totalTokens;

    /**
     * 输入 Token 数
     */
    private Integer inputTokens;

    /**
     * 输出 Token 数
     */
    private Integer outputTokens;

    /**
     * 实际使用的模型
     */
    private String modelUsed;

    /**
     * 模型版本
     */
    private String modelVersion;

    /**
     * 原始响应（调试用）
     */
    private Map<String, Object> raw;

    /**
     * 扩展信息
     */
    private Map<String, Object> extra;
}
