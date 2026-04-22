package com.actionow.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pipeline 步骤实体
 * 每个步骤定义一次 AI 生成操作，可引用前序步骤的输出
 *
 * @author Actionow
 */
@Data
@TableName(value = "t_pipeline_step", autoResultMap = true)
public class PipelineStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 所属 Pipeline ID
     */
    @TableField("pipeline_id")
    private String pipelineId;

    /**
     * 步骤编号（从1开始）
     */
    @TableField("step_number")
    private Integer stepNumber;

    /**
     * 步骤名称
     */
    private String name;

    /**
     * 步骤类型: GENERATE_TEXT / GENERATE_IMAGE / GENERATE_VIDEO / GENERATE_AUDIO / GENERATE_TTS / TRANSFORM / EXPAND
     */
    @TableField("step_type")
    private String stepType;

    /**
     * 生成类型: TEXT / IMAGE / VIDEO / AUDIO / TTS
     */
    @TableField("generation_type")
    private String generationType;

    /**
     * Provider ID
     */
    @TableField("provider_id")
    private String providerId;

    /**
     * 参数模板（支持 {{steps[N].output.xxx}} 插值引用前序步骤输出）
     */
    @TableField(value = "params_template", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> paramsTemplate;

    /**
     * 依赖的步骤编号列表
     */
    @TableField(value = "depends_on", typeHandler = JacksonTypeHandler.class)
    private List<Integer> dependsOn;

    /**
     * 扇出数量（变体/多输出）
     */
    @TableField("fan_out_count")
    private Integer fanOutCount;

    /**
     * 步骤状态: PENDING / RUNNING / COMPLETED / FAILED
     */
    private String status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
