package com.actionow.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * 生成请求公共基类
 * 抽取 SubmitGenerationRequest 与 EntityGenerationRequest 的共有字段
 *
 * @author Actionow
 */
@Data
@SuperBuilder
@NoArgsConstructor
public abstract class BaseGenerationRequest {

    /**
     * 生成类型: IMAGE/VIDEO/TEXT/AUDIO/TTS
     */
    @NotBlank(message = "生成类型不能为空")
    private String generationType;

    /**
     * 模型提供商 ID（可选，为空使用默认）
     */
    private String providerId;

    /**
     * 生成参数（所有参数统一在此传递）
     */
    @NotNull(message = "参数不能为空")
    @lombok.Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * 任务优先级
     */
    private Integer priority;

    /**
     * 响应模式: BLOCKING, STREAMING, CALLBACK, POLLING
     * 默认为 null，由系统根据提供商支持情况自动选择
     */
    @Pattern(regexp = "BLOCKING|STREAMING|CALLBACK|POLLING", message = "响应模式不合法")
    private String responseMode;

    /**
     * 任务来源（MANUAL/BATCH/RETRY）
     */
    private String source;
}
