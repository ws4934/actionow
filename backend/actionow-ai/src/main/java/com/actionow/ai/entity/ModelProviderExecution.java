package com.actionow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模型提供商执行记录实体
 * 记录每次AI模型调用的详细信息
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_model_provider_execution", autoResultMap = true)
public class ModelProviderExecution extends BaseEntity {

    /**
     * 提供商ID
     */
    private String providerId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 插件ID
     */
    private String pluginId;

    /**
     * 提供商名称（冗余存储，便于查询）
     */
    private String providerName;

    /**
     * 外部任务ID（第三方返回的ID）
     */
    private String externalTaskId;

    /**
     * 外部运行ID
     */
    private String externalRunId;

    /**
     * 输入数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputData;

    /**
     * 输出数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputData;

    /**
     * 响应模式
     * BLOCKING, STREAMING, CALLBACK, POLLING
     */
    private String responseMode;

    /**
     * 执行状态
     * PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED, TIMEOUT
     */
    private String status;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 积分消耗
     */
    private Long creditCost;

    /**
     * 执行耗时（毫秒）
     */
    private Long elapsedTime;

    /**
     * Token消耗
     */
    private Integer totalTokens;

    /**
     * 是否已收到回调
     */
    private Boolean callbackReceived;

    /**
     * 回调接收时间
     */
    private LocalDateTime callbackReceivedAt;

    /**
     * 轮询次数
     */
    private Integer pollCount;

    /**
     * 最后轮询时间
     */
    private LocalDateTime lastPolledAt;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
