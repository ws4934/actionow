package com.actionow.ai.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 插件执行请求
 * 统一的执行请求格式，屏蔽不同模型的差异
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginExecutionRequest {

    /**
     * 执行ID（内部追踪用）
     */
    private String executionId;

    /**
     * 提供商ID
     */
    private String providerId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 关联的任务ID
     */
    private String taskId;

    /**
     * 响应模式
     */
    @Builder.Default
    private ResponseMode responseMode = ResponseMode.BLOCKING;

    /**
     * 输入参数（统一格式）
     */
    private Map<String, Object> inputs;

    /**
     * 回调URL（用于CALLBACK模式）
     */
    private String callbackUrl;

    /**
     * 回调附加数据（会在回调时原样返回）
     */
    private Map<String, Object> callbackMetadata;

    /**
     * 是否跳过积分检查
     */
    @Builder.Default
    private boolean skipCreditCheck = false;

    /**
     * 请求超时覆盖（毫秒，为空使用配置默认值）
     */
    private Integer timeoutOverride;

    /**
     * 额外的请求头
     */
    private Map<String, String> extraHeaders;

    /**
     * 原始请求体（用于直接透传，不经过映射）
     */
    private Object rawRequestBody;

    /**
     * 是否使用原始请求体
     */
    @Builder.Default
    private boolean useRawBody = false;
}
