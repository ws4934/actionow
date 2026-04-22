package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型提供商执行结果
 * AI 服务返回给 Task 服务
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderExecutionResult {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 执行 ID（本系统生成的执行标识）
     */
    private String executionId;

    /**
     * 外部执行 ID（第三方服务返回的运行 ID）
     */
    private String externalRunId;

    /**
     * 外部任务 ID（用于轮询/回调查询）
     */
    private String externalTaskId;

    /**
     * 执行状态：PENDING, RUNNING, SUCCEEDED, FAILED, TIMEOUT, CANCELLED
     */
    private String status;

    /**
     * 响应模式：BLOCKING, STREAMING, CALLBACK, POLLING
     */
    private String responseMode;

    /**
     * 生成的文件 URL
     */
    private String fileUrl;

    /**
     * OSS 存储路径
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
     * 文件大小
     */
    private Long fileSize;

    /**
     * 元数据信息（尺寸、时长等）
     */
    private Map<String, Object> metaInfo;

    /**
     * 输出结果（通用）
     */
    private Map<String, Object> outputs;

    /**
     * 实际消耗积分
     */
    private Integer creditCost;

    /**
     * 执行耗时（毫秒）
     */
    private Long elapsedTime;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 转换为 Map（用于存储到 Task 的 outputResult）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("executionId", executionId);
        map.put("externalRunId", externalRunId);
        map.put("externalTaskId", externalTaskId);
        map.put("status", status);
        map.put("responseMode", responseMode);
        map.put("fileUrl", fileUrl);
        map.put("fileKey", fileKey);
        map.put("thumbnailUrl", thumbnailUrl);
        map.put("mimeType", mimeType);
        map.put("fileSize", fileSize);
        map.put("metaInfo", metaInfo);
        map.put("outputs", outputs);
        map.put("creditCost", creditCost);
        map.put("elapsedTime", elapsedTime);
        if (errorCode != null) {
            map.put("errorCode", errorCode);
        }
        if (errorMessage != null) {
            map.put("errorMessage", errorMessage);
        }
        return map;
    }

    /**
     * 判断是否为异步响应模式（需要后续轮询或等待回调）
     */
    public boolean isAsyncMode() {
        return "POLLING".equals(responseMode) || "CALLBACK".equals(responseMode);
    }

    /**
     * 判断执行是否处于挂起/运行状态（非终态）
     */
    public boolean isPending() {
        return "PENDING".equals(status) || "RUNNING".equals(status);
    }
}
