package com.actionow.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Pipeline 工作流定义实体
 * 一个 Pipeline 属于一个 BatchJob，包含多个有序步骤
 *
 * @author Actionow
 */
@Data
@TableName("t_pipeline")
public class Pipeline implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 所属批量作业ID
     */
    @TableField("batch_job_id")
    private String batchJobId;

    /**
     * Pipeline 名称
     */
    private String name;

    /**
     * 预定义模板代码
     */
    @TableField("template_code")
    private String templateCode;

    /**
     * 状态: CREATED / RUNNING / COMPLETED / FAILED
     */
    private String status;

    /**
     * 当前执行步骤编号
     */
    @TableField("current_step")
    private Integer currentStep;

    /**
     * 总步骤数
     */
    @TableField("total_steps")
    private Integer totalSteps;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
