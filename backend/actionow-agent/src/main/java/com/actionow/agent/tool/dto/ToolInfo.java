package com.actionow.agent.tool.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具信息
 * 包含工具的完整元数据，支持 PROJECT 和 AI 两类工具
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工具信息")
public class ToolInfo {

    @Schema(description = "工具 ID")
    private String toolId;

    @Schema(description = "工具类名")
    private String toolClass;

    @Schema(description = "工具方法名")
    private String toolMethod;

    @Schema(description = "工具名称（用于 Agent 调用）")
    private String toolName;

    @Schema(description = "工具显示名称")
    private String displayName;

    @Schema(description = "工具描述")
    private String description;

    @Schema(description = "工具摘要")
    private String summary;

    @Schema(description = "工具作用")
    private String purpose;

    @Schema(description = "工具分类：PROJECT | AI")
    private String category;

    @Schema(description = "工具来源：CODE_SCAN | AI_DISCOVERY | MANUAL")
    private String sourceType;

    @Schema(description = "动作类型：READ | SEARCH | WRITE | GENERATE | CONTROL | UNKNOWN")
    private String actionType;

    @Schema(description = "访问模式：FULL | READONLY | DISABLED")
    private String accessMode;

    @Schema(description = "回调名称（Spring AI Tool Definition name）")
    private String callbackName;

    @Schema(description = "参数列表")
    private List<ToolParam> params;

    @Schema(description = "输入 Schema")
    private String inputSchema;

    @Schema(description = "返回类型")
    private String returnType;

    @Schema(description = "输出定义")
    private ToolOutput output;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "每日配额，-1 表示无限")
    private Integer dailyQuota;

    @Schema(description = "当日已使用次数")
    private Integer usedToday;

    @Schema(description = "是否可用")
    private Boolean available;

    @Schema(description = "标签列表")
    private List<String> tags;

    @Schema(description = "使用说明")
    private List<String> usageNotes;

    @Schema(description = "常见错误")
    private List<String> errorCases;

    @Schema(description = "输入示例")
    private String exampleInput;

    @Schema(description = "输出示例")
    private String exampleOutput;

    @Schema(description = "关联的 Skill 名称列表")
    private List<String> skillNames;

    @Schema(description = "直接工具模式：CHAT | MISSION | null（普通 Skill 工具）")
    private String directToolMode;

    @Schema(description = "额外元数据")
    private Map<String, Object> metadata;

    /**
     * 生成工具名称（用于 Agent 调用）
     */
    public String generateToolName() {
        if (toolName != null) {
            return toolName;
        }
        // 默认使用方法名
        return toolMethod;
    }

    /**
     * 检查是否可用
     */
    public boolean isAvailable() {
        if (!Boolean.TRUE.equals(enabled)) {
            return false;
        }
        if ("DISABLED".equals(accessMode) || "QUOTA_EXHAUSTED".equals(accessMode)) {
            return false;
        }
        // 检查配额
        if (dailyQuota != null && dailyQuota >= 0 && usedToday != null) {
            return usedToday < dailyQuota;
        }
        return true;
    }
}
