package com.actionow.project.dto.inspiration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 灵感生成请求
 *
 * @author Actionow
 */
@Data
public class InspirationGenerateRequest {

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "生成类型不能为空")
    private String generationType;

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    private String negativePrompt;

    @NotBlank(message = "Provider ID不能为空")
    private String providerId;

    private Map<String, Object> params;

    private Integer count;
}
