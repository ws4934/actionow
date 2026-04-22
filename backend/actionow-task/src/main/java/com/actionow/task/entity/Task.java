package com.actionow.task.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异步任务实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_task", autoResultMap = true)
public class Task extends TenantBaseEntity {

    /**
     * 任务类型
     */
    @TableField("task_type")
    private String type;

    /**
     * 任务标题
     */
    private String title;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 进度（0-100）
     */
    private Integer progress;

    /**
     * 所属剧本ID
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 目标实体ID（剧集、分镜、角色、场景、道具等）
     */
    @TableField("entity_id")
    private String entityId;

    /**
     * 实体类型: EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE, ASSET
     */
    @TableField("entity_type")
    private String entityType;

    /**
     * 实体名称（创建时快照）
     */
    @TableField("entity_name")
    private String entityName;

    /**
     * AI 模型提供商ID
     */
    @TableField("provider_id")
    private String providerId;

    /**
     * 生成类型: IMAGE, VIDEO, AUDIO, TEXT
     */
    @TableField("generation_type")
    private String generationType;

    /**
     * 缩略图URL（任务完成后回填）
     */
    @TableField("thumbnail_url")
    private String thumbnailUrl;

    /**
     * 实际消耗积分（任务完成后回填）
     */
    @TableField("credit_cost")
    private Integer creditCost;

    /**
     * 任务来源: MANUAL, BATCH, RETRY, SCHEDULED
     */
    private String source;

    /**
     * 输入参数 (JSON)
     * <p>
     * 除业务参数外，还包含以下内部跟踪字段（由 AiGenerationFacade 写入）：
     * <pre>
     * - executionId:       AI 模块 ModelProviderExecution 的 ID，
     *                      POLLING 模式下 PollingTaskScanner 通过此字段查询执行状态，
     *                      避免跨服务查询时需要额外的 Feign 调用。
     * - actualResponseMode: 实际响应模式（BLOCKING / CALLBACK / POLLING），
     *                      用于扫描器按模式分类处理。
     * - transactionId:     积分冻结事务 ID，用于 confirmConsume / unfreeze。
     * - creditCost:        冻结的积分数量。
     * </pre>
     */
    @TableField(value = "input_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputParams;

    /**
     * 输出结果 (JSON)
     */
    @TableField(value = "output_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputResult;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 错误详情 (JSON)
     */
    @TableField(value = "error_detail", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> errorDetail;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    @TableField("max_retries")
    private Integer maxRetry;

    /**
     * 开始时间
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /**
     * 超时时间（秒）
     */
    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    /**
     * 错误码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 超时时间点
     */
    @TableField("timeout_at")
    private LocalDateTime timeoutAt;

    /**
     * 创建者ID
     */
    @TableField("creator_id")
    private String creatorId;

    /**
     * 所属批量作业ID
     */
    @TableField("batch_job_id")
    private String batchJobId;

    /**
     * 所属批量作业子项ID
     */
    @TableField("batch_item_id")
    private String batchItemId;
}
