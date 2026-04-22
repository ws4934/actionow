package com.actionow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 复制模型提供商请求
 *
 * @author Actionow
 */
@Data
public class CopyModelProviderRequest {

    /**
     * 新提供商名称
     */
    @NotBlank(message = "新名称不能为空")
    private String newName;

    /**
     * 新提供商描述（可选，为空则复制原描述）
     */
    private String newDescription;

    /**
     * 是否启用（可选，默认 false）
     */
    private Boolean enabled = false;
}
