package com.actionow.agent.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量实体生成请求 DTO
 *
 * @author Actionow
 */
@Data
public class BatchEntityGenerationRequest {

    /**
     * 生成请求列表（限 200 条/次，避免一次请求造成巨量后端任务）
     */
    @NotEmpty(message = "requests 不能为空")
    @Size(max = 200, message = "单次批量提交不得超过 200 条")
    @Valid
    private List<EntityGenerationRequest> requests;

    /**
     * 是否并行处理（可选）
     * true - 并行提交所有任务
     * false（默认）- 顺序提交
     */
    private Boolean parallel = false;
}
