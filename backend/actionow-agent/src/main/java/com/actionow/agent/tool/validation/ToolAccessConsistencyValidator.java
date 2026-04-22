package com.actionow.agent.tool.validation;

import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.entity.AgentToolAccess;
import com.actionow.agent.tool.mapper.AgentToolAccessMapper;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时工具访问配置一致性校验
 *
 * <p>检查 ProjectToolRegistry 中已注册的工具在 t_agent_tool_access 表中
 * 是否至少有一条访问记录。缺失记录意味着该工具在任何 Agent 中都不可见。
 *
 * @author Actionow
 */
@Slf4j
@Component
@DependsOn("projectToolScanner")
@RequiredArgsConstructor
public class ToolAccessConsistencyValidator {

    private final ProjectToolRegistry toolRegistry;
    private final AgentToolAccessMapper toolAccessMapper;

    @PostConstruct
    public void validate() {
        List<ToolInfo> allTools = toolRegistry.getAllProjectTools();
        List<String> warnings = new ArrayList<>();

        for (ToolInfo tool : allTools) {
            List<AgentToolAccess> accesses = toolAccessMapper.selectByToolId(tool.getToolId());
            if (accesses.isEmpty()) {
                warnings.add("已注册工具 '" + tool.getToolId() + "' ("
                        + tool.getToolName() + ") 在 t_agent_tool_access 中无任何访问记录，"
                        + "任何 Agent 都无法使用该工具");
            }
        }

        if (!warnings.isEmpty()) {
            log.warn("===== 工具访问配置一致性检查: {} 个问题 =====", warnings.size());
            warnings.forEach(w -> log.warn("  - {}", w));
        } else {
            log.info("工具访问配置一致性检查通过 ({} 个工具)", allTools.size());
        }
    }
}
