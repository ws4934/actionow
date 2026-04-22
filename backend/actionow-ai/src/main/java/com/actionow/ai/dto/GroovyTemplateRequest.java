package com.actionow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 创建/更新Groovy模板请求DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroovyTemplateRequest {

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称最长200字符")
    private String name;

    /**
     * 模板描述
     */
    @Size(max = 1000, message = "描述最长1000字符")
    private String description;

    /**
     * 模板类型
     */
    @NotBlank(message = "模板类型不能为空")
    private String templateType;

    /**
     * 适用的生成类型
     */
    private String generationType;

    /**
     * 脚本内容
     */
    @NotBlank(message = "脚本内容不能为空")
    private String scriptContent;

    /**
     * 脚本版本
     */
    private String scriptVersion;

    /**
     * 示例输入
     */
    private Map<String, Object> exampleInput;

    /**
     * 示例输出
     */
    private Map<String, Object> exampleOutput;

    /**
     * 文档说明
     */
    private String documentation;

    /**
     * 是否启用
     */
    private Boolean enabled;
}
