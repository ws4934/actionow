package com.actionow.agent.core.scope;

import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.feign.ProjectFeignClient;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.security.workspace.WorkspaceInternalClient;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一的 AgentContext 构建器。
 *
 * <p>CHAT 路径 (AgentPreflightService) 和 MISSION 路径 (MissionExecutor) 共享同一套
 * 上下文构建逻辑，避免两条路径因独立实现而产生字段遗漏或不一致。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentContextBuilder {

    private final ProjectFeignClient projectFeignClient;
    private final WorkspaceInternalClient workspaceInternalClient;

    /**
     * 构建 AgentContext 并注册到 ThreadLocal + SessionContextHolder。
     *
     * @param params 构建参数
     * @return 构建完成的 AgentContext
     */
    public AgentContext buildAndRegister(ContextParams params) {
        // 1. UserContext（租户 Schema 切换）
        UserContext userContext = new UserContext();
        userContext.setUserId(params.getUserId());
        userContext.setWorkspaceId(params.getWorkspaceId());
        userContext.setTenantSchema(resolveTenantSchema(params.getWorkspaceId(), params.getTenantSchema()));
        UserContextHolder.setContext(userContext);

        // 2. Scope 解析
        AgentScope scope = params.getScope() != null
                ? params.getScope()
                : (params.getScriptId() != null ? AgentScope.SCRIPT : AgentScope.GLOBAL);

        // 3. ScriptName 解析
        String scriptName = params.getScriptName() != null
                ? params.getScriptName()
                : resolveScriptName(params.getScriptId());

        // 4. 构建 AgentContext
        AgentContext context = AgentContext.builder()
                .scope(scope)
                .sessionId(params.getSessionId())
                .agentType(params.getAgentType())
                .skillNames(params.getSkillNames())
                .executionMode(params.getExecutionMode())
                .scriptId(params.getScriptId())
                .scriptName(scriptName)
                .userId(params.getUserId())
                .workspaceId(params.getWorkspaceId())
                .tenantId(params.getWorkspaceId())
                .missionId(params.getMissionId())
                .missionStepId(params.getMissionStepId())
                .episodeId(params.getEpisodeId())
                .storyboardId(params.getStoryboardId())
                .characterId(params.getCharacterId())
                .sceneId(params.getSceneId())
                .propId(params.getPropId())
                .styleId(params.getStyleId())
                .assetId(params.getAssetId())
                .build();

        // 5. 注册到 Holder
        AgentContextHolder.setContext(context);
        if (params.getSessionId() != null) {
            SessionContextHolder.setCurrentSessionId(params.getSessionId());
            SessionContextHolder.set(params.getSessionId(), userContext, context);
        }

        return context;
    }

    private String resolveTenantSchema(String workspaceId, String snapshot) {
        if (snapshot != null && !snapshot.isBlank()) {
            return snapshot;
        }
        if (workspaceId == null) {
            return null;
        }
        try {
            var result = workspaceInternalClient.getTenantSchema(workspaceId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve tenant schema for workspaceId={}: {}", workspaceId, e.getMessage());
        }
        // fallback: derive from workspaceId
        return "tenant_" + workspaceId.replace("-", "");
    }

    private String resolveScriptName(String scriptId) {
        if (scriptId == null) {
            return null;
        }
        try {
            var result = projectFeignClient.getScript(scriptId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                Object title = result.getData().get("title");
                if (title == null) {
                    title = result.getData().get("name");
                }
                return title != null ? title.toString() : null;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve script name for scriptId={}: {}", scriptId, e.getMessage());
        }
        return null;
    }

    /**
     * 上下文构建参数。
     */
    @Getter
    @Builder
    public static class ContextParams {
        private String sessionId;
        private String agentType;
        private List<String> skillNames;
        private String executionMode;
        private String userId;
        private String workspaceId;
        private String tenantSchema;
        private AgentScope scope;
        private String scriptId;
        private String scriptName;
        private String missionId;
        private String missionStepId;
        // CHAT 路径锚点
        private String episodeId;
        private String storyboardId;
        private String characterId;
        private String sceneId;
        private String propId;
        private String styleId;
        private String assetId;
    }
}
