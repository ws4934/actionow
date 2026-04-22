package com.actionow.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 Skill 请求 DTO
 *
 * @author Actionow
 */
@Data
public class SkillCreateRequest {

    /** Skill 唯一名称，格式: [a-z][a-z0-9_]{1,63} */
    @NotBlank(message = "name 不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "name 格式无效，需以小写字母开头，仅含小写字母、数字和下划线，长度 2-64")
    private String name;

    /** 展示名称（中文） */
    private String displayName;

    /** 简短描述（必填，注入 LLM 的 Skill 元数据） */
    @NotBlank(message = "description 不能为空")
    private String description;

    /** 完整指令内容（即专家 System Prompt） */
    @NotBlank(message = "content 不能为空")
    private String content;

    /**
     * 关联工具类 ID 列表
     * 格式: "{classPrefix}_{methodName}"
     */
    private List<String> groupedToolIds;

    /**
     * 结构化输出 JSON Schema（null 表示自由文本输出）
     */
    private Map<String, Object> outputSchema;

    /** 标签列表，用于分类和检索 */
    private List<String> tags;

    /** 参考资料列表，每项格式: {"title":"...","url":"...","description":"..."} */
    private List<Map<String, Object>> references;

    /** 使用示例列表，每项格式: {"title":"...","content":"...","input":"...","output":"..."} */
    private List<Map<String, Object>> examples;

    /** 作用域: SYSTEM | WORKSPACE，默认 SYSTEM */
    private String scope;

    private String workspaceId;
}
