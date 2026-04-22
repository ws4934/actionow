package com.actionow.task.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 重试生成请求
 * 从 Asset.extraInfo 读取原参数并合并覆盖
 * <p>
 * 所有覆盖参数统一通过 params 传递，与原参数合并。
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryGenerationRequest {

    /**
     * 素材 ID（必填）
     */
    @NotBlank(message = "素材ID不能为空")
    private String assetId;

    /**
     * 模型提供商 ID（可选，覆盖原参数）
     */
    private String providerId;

    /**
     * 生成参数（可选，合并覆盖原参数）
     * 可包含 prompt, negative_prompt 等任意参数
     */
    private Map<String, Object> params;

    /**
     * 任务优先级（可选，覆盖原参数）
     */
    private Integer priority;
}
