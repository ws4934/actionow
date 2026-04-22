package com.actionow.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 提示词模板响应
 *
 * @author Actionow
 */
@Data
public class PromptTemplateResponse {

    /**
     * 模板ID
     */
    private String id;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 生成类型
     */
    private String type;

    /**
     * 模板内容
     */
    private String content;

    /**
     * 负面提示词
     */
    private String negativePrompt;

    /**
     * 变量定义
     */
    private List<Map<String, Object>> variables;

    /**
     * 默认参数
     */
    private Map<String, Object> defaultParams;

    /**
     * 作用域
     */
    private String scope;

    /**
     * 状态
     */
    private String status;

    /**
     * 使用次数
     */
    private Long useCount;

    /**
     * 创建者ID
     */
    private String creatorId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
