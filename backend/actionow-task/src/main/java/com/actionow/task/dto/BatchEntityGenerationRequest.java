package com.actionow.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量实体生成请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEntityGenerationRequest {

    /**
     * 生成请求列表
     */
    @NotEmpty(message = "请求列表不能为空")
    @Valid
    private List<EntityGenerationRequest> requests;

    /**
     * 是否并行处理（默认顺序处理）
     */
    @Builder.Default
    private Boolean parallel = false;

    /**
     * 批次名称（可选）
     */
    private String batchName;

    /**
     * 遇错是否停止（仅顺序模式生效）
     */
    @Builder.Default
    private Boolean stopOnError = false;
}
