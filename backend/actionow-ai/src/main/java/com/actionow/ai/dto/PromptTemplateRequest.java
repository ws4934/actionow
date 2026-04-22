package com.actionow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板请求
 * 用于创建和更新提示词模板
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplateRequest {

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过100字符")
    private String name;

    /**
     * 模板描述
     */
    @Size(max = 500, message = "模板描述长度不能超过500字符")
    private String description;

    /**
     * 生成类型（IMAGE/VIDEO/AUDIO/TEXT）
     */
    @NotBlank(message = "生成类型不能为空")
    private String type;

    /**
     * 模板内容（支持变量占位符）
     */
    @NotBlank(message = "模板内容不能为空")
    @Size(max = 10000, message = "模板内容长度不能超过10000字符")
    private String content;

    /**
     * 负面提示词
     */
    @Size(max = 5000, message = "负面提示词长度不能超过5000字符")
    private String negativePrompt;

    /**
     * 变量定义列表
     * 每个元素包含: name(变量名), type(类型), label(标签), description(描述),
     * required(是否必填), defaultValue(默认值), options(选项列表)
     */
    private List<Map<String, Object>> variables;

    /**
     * 默认参数
     */
    private Map<String, Object> defaultParams;
}
