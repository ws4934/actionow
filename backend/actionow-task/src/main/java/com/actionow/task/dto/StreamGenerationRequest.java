package com.actionow.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 流式生成请求
 * <p>
 * 所有生成参数统一通过 params 传递，不对具体参数做特殊处理。
 * 不同模型可能有不同的参数需求，保持最大灵活性。
 *
 * @author Actionow
 */
@Data
public class StreamGenerationRequest {

    /**
     * 模型提供商ID
     */
    @NotBlank(message = "providerId不能为空")
    private String providerId;

    /**
     * 生成参数（所有参数统一在此传递，如 prompt, negative_prompt, 尺寸、质量等）
     */
    @NotNull(message = "参数不能为空")
    private Map<String, Object> params = new HashMap<>();

    /**
     * 关联素材ID（可选）
     */
    private String assetId;

    /**
     * 关联业务ID（可选）
     */
    private String businessId;

    /**
     * 业务类型（可选）
     */
    private String businessType;
}
