package com.actionow.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工作空间请求
 *
 * @author Actionow
 */
@Data
public class CreateWorkspaceRequest {

    /**
     * 工作空间名称
     */
    @NotBlank(message = "工作空间名称不能为空")
    @Size(min = 2, max = 100, message = "工作空间名称长度为2-100个字符")
    private String name;

    /**
     * URL友好标识（可选，不提供则根据名称自动生成）
     */
    @Size(min = 2, max = 100, message = "slug长度为2-100个字符")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "slug只能包含小写字母、数字和连字符")
    private String slug;

    /**
     * 工作空间描述
     */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /**
     * 工作空间Logo URL
     */
    private String logoUrl;
}
