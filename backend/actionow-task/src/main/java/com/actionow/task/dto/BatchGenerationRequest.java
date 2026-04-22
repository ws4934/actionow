package com.actionow.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量生成任务请求
 *
 * @author Actionow
 */
@Data
public class BatchGenerationRequest {

    /**
     * 批量任务列表
     */
    @NotEmpty(message = "任务列表不能为空")
    @Size(max = 10, message = "单次最多提交10个任务")
    @Valid
    private List<SubmitGenerationRequest> tasks;

    /**
     * 是否串行执行（按顺序执行）
     * 默认为false，即并行执行
     */
    private boolean sequential = false;

    /**
     * 批量任务名称（可选）
     */
    private String batchName;

    /**
     * 是否在任一失败时停止（仅串行模式有效）
     */
    private boolean stopOnError = false;
}
