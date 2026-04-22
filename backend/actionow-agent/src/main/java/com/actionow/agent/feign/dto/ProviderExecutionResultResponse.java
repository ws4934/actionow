package com.actionow.agent.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI Provider 执行结果响应
 * 对应 actionow-ai 的 ProviderExecutionResultResponse
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderExecutionResultResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 执行 ID
     */
    private String executionId;

    /**
     * 外部平台 Run ID
     */
    private String externalRunId;

    /**
     * 外部平台 Task ID
     */
    private String externalTaskId;

    /**
     * 状态：PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED
     */
    private String status;

    /**
     * 响应模式
     */
    private String responseMode;

    /**
     * 生成的文件 URL
     */
    private String fileUrl;

    /**
     * 缩略图 URL
     */
    private String thumbnailUrl;

    /**
     * 文件 MIME 类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 元信息
     */
    private Map<String, Object> metaInfo;

    /**
     * 完整输出数据
     */
    private Map<String, Object> outputs;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * 执行耗时（毫秒）
     */
    private Long elapsedTimeMs;
}
