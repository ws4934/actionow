package com.actionow.ai.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 执行指标
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionMetrics {

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 模型提供商ID
     */
    private String providerId;

    /**
     * 模型提供商名称
     */
    private String providerName;

    /**
     * 插件ID
     */
    private String pluginId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 响应模式
     */
    private String responseMode;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 总耗时（毫秒）
     */
    private Long totalDurationMs;

    /**
     * 脚本准备耗时（毫秒）
     */
    private Long scriptPrepareMs;

    /**
     * 脚本执行耗时（毫秒）
     */
    private Long scriptExecuteMs;

    /**
     * API调用耗时（毫秒）
     */
    private Long apiCallMs;

    /**
     * 后处理耗时（毫秒）
     */
    private Long postProcessMs;

    /**
     * 输入token数（如适用）
     */
    private Integer inputTokens;

    /**
     * 输出token数（如适用）
     */
    private Integer outputTokens;

    /**
     * 生成的文件大小（字节）
     */
    private Long outputFileSize;

    /**
     * 消耗积分
     */
    private Long creditsCost;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return "SUCCEEDED".equals(status) || "COMPLETED".equals(status);
    }

    /**
     * 是否失败
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || "ERROR".equals(status);
    }
}
