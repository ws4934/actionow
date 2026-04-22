package com.actionow.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建任务请求
 *
 * @author Actionow
 */
@Data
public class CreateTaskRequest {

    /**
     * 任务类型
     */
    @NotBlank(message = "任务类型不能为空")
    private String type;

    /**
     * 任务标题
     */
    private String title;

    /**
     * 优先级（默认5，范围 1-10）
     */
    @Min(value = 1, message = "优先级最小为1")
    @Max(value = 10, message = "优先级最大为10")
    private Integer priority;

    /**
     * 所属剧本ID
     */
    private String scriptId;

    /**
     * 目标实体ID
     */
    private String entityId;

    /**
     * 实体类型: EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE, ASSET
     */
    private String entityType;

    /**
     * 实体名称（可选，创建时快照）
     */
    private String entityName;

    /**
     * AI 模型提供商ID
     */
    private String providerId;

    /**
     * 生成类型: IMAGE, VIDEO, AUDIO, TEXT
     */
    private String generationType;

    /**
     * 任务来源: MANUAL, BATCH, RETRY, SCHEDULED
     */
    private String source;

    /**
     * 输入参数
     */
    private Map<String, Object> inputParams;

    /**
     * 超时时间（秒，范围 10-3600）
     */
    @Min(value = 10, message = "超时时间最少10秒")
    @Max(value = 3600, message = "超时时间最多3600秒")
    private Integer timeoutSeconds;
}
