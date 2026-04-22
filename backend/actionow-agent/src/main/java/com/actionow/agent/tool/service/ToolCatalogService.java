package com.actionow.agent.tool.service;

import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.common.core.result.PageResult;

import java.util.List;

/**
 * Tool Catalog 查询服务。
 */
public interface ToolCatalogService {

    /**
     * 分页查询工具目录。
     */
    PageResult<ToolInfo> findPage(Long current, Long size, String keyword, String actionType, String tag, String workspaceId);

    /**
     * 获取工具详情。
     */
    ToolInfo getTool(String toolId, String workspaceId);

    /**
     * 获取指定 Skill 绑定的工具详情列表。
     */
    List<ToolInfo> getToolsForSkill(String skillName, String workspaceId);
}
