package com.actionow.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
/**
 * Agent Skill 实体（数据库驱动的 Skill 定义）
 *
 * @author Actionow
 */
@Data
@TableName(value = "t_agent_skill", autoResultMap = true)
public class AgentSkillEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** Skill 唯一标识名，格式: [a-z][a-z0-9_]{1,63} */
    private String name;

    /** 展示名称（中文） */
    private String displayName;

    /** 简短描述，注入 LLM 的 Skill 元数据 */
    private String description;

    /** 完整指令内容（即原专家 System Prompt 内容） */
    private String content;

    /**
     * 关联的工具 ID 列表
     * 格式: "{classPrefix}_{methodName}"，由 ProjectToolScanner 生成
     * 每个 ID 对应一个精确工具方法（而非整个 Bean）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> groupedToolIds;

    /**
     * 结构化输出 JSON Schema（null 表示自由文本输出，无需调用 output_structured_result）
     * 格式参考 JSON Schema Draft-07，使用 "required" 字段声明必填字段
     * <p>
     * 注意：DB 中可能存储为 JSON Object 或 Array（历史数据），使用 Object 类型兼容两者。
     * 通过 {@link #getOutputSchema()} 确保返回 Map 或 null。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object outputSchema;

    /**
     * 获取 outputSchema 作为 Map（JSON Schema 对象）。
     * 若底层数据为数组或非 Map 类型，返回 null。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOutputSchema() {
        if (outputSchema instanceof Map) {
            return (Map<String, Object>) outputSchema;
        }
        return null;
    }

    /**
     * 标签列表，用于分类和检索
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 参考资料列表（对应标准 Skill references/ 目录）
     * 每项格式: {"title": "...", "url": "...", "description": "..."}
     * NOTE: "references" is a PostgreSQL reserved keyword; column name must be quoted.
     */
    @TableField(value = "\"references\"", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> references;

    /**
     * 使用示例列表（对应标准 Skill examples/ 目录）
     * 每项格式: {"title": "...", "content": "...", "input": "...", "output": "..."}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> examples;

    private Boolean enabled;

    /** 版本号（每次 update 自增） */
    private Integer version;

    /** 作用域: SYSTEM | WORKSPACE | USER */
    private String scope;

    private String workspaceId;

    private String creatorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;

    private LocalDateTime deletedAt;

    /**
     * 判断是否已删除
     */
    public boolean getIsDeleted() {
        return deleted != null && deleted != 0;
    }
}
