package com.actionow.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 重试生成请求 DTO
 * 基于已有素材重新提交生成任务
 * <p>
 * 所有覆盖参数统一通过 params 传递，与原参数合并。
 *
 * @author Actionow
 */
@Data
public class RetryGenerationRequest {

    /**
     * 素材 ID（必填）
     * 从该素材的 extraInfo 读取原始生成参数
     */
    @NotBlank(message = "assetId 不能为空")
    private String assetId;

    /**
     * 模型提供商 ID（可选）
     * 不指定则使用原参数
     */
    private String providerId;

    /**
     * 生成参数（可选）
     * 合并覆盖原参数，可包含 prompt, negative_prompt 等任意参数
     */
    private Map<String, Object> params;

    /**
     * 优先级（可选）
     * 不指定则使用原参数
     */
    private Integer priority;
}
