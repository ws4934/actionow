package com.actionow.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Groovy模板响应DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroovyTemplateResponse {

    /**
     * 模板ID
     */
    private String id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 模板类型
     */
    private String templateType;

    /**
     * 适用的生成类型
     */
    private String generationType;

    /**
     * 脚本内容
     */
    private String scriptContent;

    /**
     * 脚本版本
     */
    private String scriptVersion;

    /**
     * 是否系统模板
     */
    private Boolean isSystem;

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

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
