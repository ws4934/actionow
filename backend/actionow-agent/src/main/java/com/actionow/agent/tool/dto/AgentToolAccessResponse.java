package com.actionow.agent.tool.dto;

import com.actionow.agent.tool.entity.AgentToolAccess;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 工具访问权限响应 DTO
 *
 * @author Actionow
 */
@Data
public class AgentToolAccessResponse {

    private String id;

    /**
     * Agent 类型
     */
    private String agentType;

    /**
     * 工具分类
     */
    private String toolCategory;

    /**
     * 工具标识
     */
    private String toolId;

    /**
     * 工具显示名称
     */
    private String toolName;

    /**
     * 工具描述
     */
    private String toolDescription;

    /**
     * 访问模式
     */
    private String accessMode;

    /**
     * 每日调用限额
     */
    private Integer dailyQuota;

    /**
     * 优先级
     */
    private Integer priority;

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

    /**
     * 从实体转换
     */
    public static AgentToolAccessResponse fromEntity(AgentToolAccess entity) {
        if (entity == null) {
            return null;
        }
        AgentToolAccessResponse response = new AgentToolAccessResponse();
        response.setId(entity.getId());
        response.setAgentType(entity.getAgentType());
        response.setToolCategory(entity.getToolCategory());
        response.setToolId(entity.getToolId());
        response.setToolName(entity.getToolName());
        response.setToolDescription(entity.getToolDescription());
        response.setAccessMode(entity.getAccessMode());
        response.setDailyQuota(entity.getDailyQuota());
        response.setPriority(entity.getPriority());
        response.setEnabled(entity.getEnabled());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
