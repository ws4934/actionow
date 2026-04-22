package com.actionow.workspace.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新工作空间请求
 *
 * @author Actionow
 */
@Data
public class UpdateWorkspaceRequest {

    /**
     * 工作空间名称
     */
    @Size(min = 2, max = 50, message = "工作空间名称长度为2-50个字符")
    private String name;

    /**
     * 工作空间描述
     */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /**
     * 工作空间Logo URL
     */
    private String logoUrl;

    /**
     * 空间级配置（JSONB）
     */
    private Map<String, Object> config;
}
