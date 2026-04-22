package com.actionow.agent.tool.interceptor;

import com.actionow.agent.tool.service.AgentToolAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具执行拦截器
 * 处理工具执行前后的逻辑，包括配额检查和计数
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutionInterceptor {

    private final AgentToolAccessService toolAccessService;

    /**
     * 工具执行前检查
     *
     * @param agentType    Agent 类型
     * @param toolCategory 工具分类
     * @param toolId       工具 ID
     * @param userId       用户 ID
     * @return 是否允许执行
     */
    public boolean preExecute(String agentType, String toolCategory, String toolId, String userId) {
        // 检查访问权限
        if (!toolAccessService.hasAccess(agentType, toolCategory, toolId)) {
            log.warn("工具访问被拒绝: agentType={}, toolCategory={}, toolId={}, userId={}",
                    agentType, toolCategory, toolId, userId);
            return false;
        }

        // 检查配额
        if (!toolAccessService.checkQuota(agentType, toolCategory, toolId, userId)) {
            log.warn("工具配额已用尽: agentType={}, toolCategory={}, toolId={}, userId={}",
                    agentType, toolCategory, toolId, userId);
            return false;
        }

        return true;
    }

    /**
     * 工具执行后处理
     * 增加配额计数
     *
     * @param userId       用户 ID
     * @param toolCategory 工具分类
     * @param toolId       工具 ID
     * @param success      是否执行成功
     */
    public void postExecute(String userId, String toolCategory, String toolId, boolean success) {
        if (success) {
            // 只有成功执行才计数
            long count = toolAccessService.incrementQuotaCount(userId, toolCategory, toolId);
            log.debug("工具配额计数增加: userId={}, toolCategory={}, toolId={}, currentCount={}",
                    userId, toolCategory, toolId, count);
        }
    }
}
