package com.actionow.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 模型提供商执行结果响应
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
     * 执行 ID（内部标识）
     */
    private String executionId;

    /**
     * 外部运行 ID（第三方平台的运行ID）
     */
    private String externalRunId;

    /**
     * 外部任务 ID（第三方平台的任务ID）
     */
    private String externalTaskId;

    /**
     * 执行状态
     * PENDING - 等待中
     * RUNNING - 执行中
     * SUCCEEDED - 成功
     * FAILED - 失败
     * CANCELLED - 已取消
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
     * 生成的文件 URL
     */
    private String fileKey;

    /**
     * 缩略图 URL
     */
    private String thumbnailUrl;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 元数据信息
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
     * 错误码
     */
    private String errorCode;

    /**
     * 执行耗时（毫秒）
     */
    private Long elapsedTimeMs;
}
