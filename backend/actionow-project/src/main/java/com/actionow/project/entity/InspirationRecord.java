package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 灵感生成记录实体。
 *
 * <p><b>已 deprecated</b>：被 Asset + EntityRelation 统一流程取代。
 * prompt/status/taskId 等字段未来迁移至 Asset.extraInfo 与 task 模块。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_inspiration_record", autoResultMap = true)
public class InspirationRecord extends BaseEntity {

    @TableField("session_id")
    private String sessionId;

    private String prompt;

    @TableField("negative_prompt")
    private String negativePrompt;

    @TableField("generation_type")
    private String generationType;

    @TableField("provider_id")
    private String providerId;

    @TableField("provider_name")
    private String providerName;

    @TableField("provider_icon_url")
    private String providerIconUrl;

    @TableField(value = "params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> params;

    private String status;

    @TableField("task_id")
    private String taskId;

    @TableField("credit_cost")
    private BigDecimal creditCost;

    private Integer progress;

    @TableField("error_message")
    private String errorMessage;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
