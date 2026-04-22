package com.actionow.agent.tool.registry;

import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.dto.ToolOutput;
import com.actionow.agent.tool.dto.ToolParam;
import com.actionow.agent.tool.entity.AgentToolAccess;
import com.actionow.agent.tool.mapper.AgentToolAccessMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一工具注册表
 * 管理 PROJECT 工具的注册和查询
 *
 * AI 工具已迁移至 AiToolDiscoveryService 动态获取
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectToolRegistry {

    private final AgentToolAccessMapper toolAccessMapper;
    private final ApplicationContext applicationContext;

    // 缓存 PROJECT 工具定义（静态工具）
    private final Map<String, ToolInfo> projectToolDefinitions = new ConcurrentHashMap<>();

    // 缓存 PROJECT 工具 Bean 名称；真正实例按需延迟获取
    private final Map<String, String> projectToolBeanNames = new ConcurrentHashMap<>();

    // beanName -> toolId 列表
    private final Map<String, Set<String>> projectToolIdsByBeanName = new ConcurrentHashMap<>();

    // 已解析过的 PROJECT 工具 Bean 实例缓存
    private final Map<String, Object> projectToolBeans = new ConcurrentHashMap<>();

    // 已解析的 PROJECT 工具回调缓存（精确到 method/tool）
    private final Map<String, ToolCallback> projectToolCallbacks = new ConcurrentHashMap<>();

    // beanName -> callbackName -> ToolCallback
    private final Map<String, Map<String, ToolCallback>> beanCallbacks = new ConcurrentHashMap<>();

    /**
     * 注册 PROJECT 工具定义
     */
    public void registerProjectTool(ToolInfo toolInfo) {
        if (toolInfo == null || toolInfo.getToolId() == null) {
            return;
        }
        toolInfo.setCategory("PROJECT");
        projectToolDefinitions.put(toolInfo.getToolId(), toolInfo);
        log.info("注册 PROJECT 工具: {}", toolInfo.getToolId());
    }

    /**
     * 注册 PROJECT 工具 Bean 名称
     *
     * @param toolId   工具 ID
     * @param beanName Spring Bean 名称
     */
    public void registerProjectToolBean(String toolId, String beanName) {
        if (toolId == null || beanName == null || beanName.isBlank()) {
            return;
        }
        projectToolBeanNames.put(toolId, beanName);
        projectToolIdsByBeanName.computeIfAbsent(beanName, key -> ConcurrentHashMap.newKeySet()).add(toolId);
        log.debug("注册 PROJECT 工具 BeanName: {} -> {}", toolId, beanName);
    }

    /**
     * 获取 PROJECT 工具 Bean
     * 支持精确匹配和后缀匹配（兼容数据库中不带前缀的 toolId）
     *
     * @param toolId 工具 ID（可能带前缀，也可能不带）
     * @return 工具 Bean 实例
     */
    public Optional<Object> getProjectToolBean(String toolId) {
        String fullToolId = findFullToolId(toolId);
        if (fullToolId == null) {
            return Optional.empty();
        }

        String beanName = projectToolBeanNames.get(fullToolId);
        if (beanName == null) {
            return Optional.empty();
        }

        Object cachedBean = projectToolBeans.get(beanName);
        if (cachedBean != null) {
            return Optional.of(cachedBean);
        }

        try {
            Object resolvedBean = applicationContext.getBean(beanName);
            projectToolBeans.put(beanName, resolvedBean);
            return Optional.of(resolvedBean);
        } catch (Exception e) {
            log.warn("获取 PROJECT 工具 Bean 失败: toolId={}, beanName={}, error={}",
                    fullToolId, beanName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取所有已注册的 PROJECT 工具
     */
    public List<ToolInfo> getAllProjectTools() {
        return projectToolDefinitions.values().stream()
                .map(this::copyToolInfo)
                .sorted((left, right) -> left.getToolId().compareToIgnoreCase(right.getToolId()))
                .toList();
    }

    /**
     * 按 callback/tool 名称批量查找工具定义。
     */
    public List<ToolInfo> getProjectToolsByCallbackNames(Collection<String> callbackNames) {
        if (callbackNames == null || callbackNames.isEmpty()) {
            return List.of();
        }
        Set<String> requested = callbackNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());

        return getAllProjectTools().stream()
                .filter(tool -> {
                    String callbackName = tool.getCallbackName() != null ? tool.getCallbackName() : tool.getToolName();
                    return callbackName != null && requested.contains(callbackName);
                })
                .toList();
    }

    /**
     * 获取指定 PROJECT 工具定义
     */
    public Optional<ToolInfo> getProjectTool(String toolId) {
        String fullToolId = findFullToolId(toolId);
        ToolInfo definition = findProjectToolDefinition(fullToolId);
        if (definition == null) {
            return Optional.empty();
        }

        ToolInfo copied = copyToolInfo(definition);
        getProjectToolCallback(fullToolId).ifPresent(callback -> enrichWithCallbackMetadata(copied, callback));
        return Optional.of(copied);
    }

    /**
     * 批量获取 PROJECT 工具定义（按传入顺序返回）
     */
    public List<ToolInfo> getProjectTools(Collection<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        List<ToolInfo> result = new ArrayList<>();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (String toolId : toolIds) {
            getProjectTool(toolId).ifPresent(toolInfo -> {
                if (seen.add(toolInfo.getToolId())) {
                    result.add(toolInfo);
                }
            });
        }
        return result;
    }

    /**
     * 获取精确到方法级的 ToolCallback。
     */
    public Optional<ToolCallback> getProjectToolCallback(String toolId) {
        String fullToolId = findFullToolId(toolId);
        if (fullToolId == null) {
            return Optional.empty();
        }

        ToolCallback cached = projectToolCallbacks.get(fullToolId);
        if (cached != null) {
            return Optional.of(cached);
        }

        String beanName = projectToolBeanNames.get(fullToolId);
        if (beanName == null) {
            return Optional.empty();
        }

        beanCallbacks.computeIfAbsent(beanName, this::resolveCallbacksForBean);
        return Optional.ofNullable(projectToolCallbacks.get(fullToolId));
    }

    /**
     * 查找 PROJECT 工具定义
     * 支持精确匹配和后缀匹配（兼容数据库中不带前缀的 toolId）
     *
     * @param toolId 工具 ID（可能带前缀如 multimodal_batchGetAssets，也可能不带如 batchGetAssets）
     * @return 匹配的工具定义，如果未找到返回 null
     */
    private ToolInfo findProjectToolDefinition(String toolId) {
        if (toolId == null) {
            return null;
        }

        ToolInfo exact = projectToolDefinitions.get(toolId);
        if (exact != null) {
            return exact;
        }

        String suffix = "_" + toolId;
        for (Map.Entry<String, ToolInfo> entry : projectToolDefinitions.entrySet()) {
            if (entry.getKey().endsWith(suffix)) {
                log.debug("Tool ID suffix match: {} -> {}", toolId, entry.getKey());
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 查找 PROJECT 工具的完整 ID（带前缀）
     *
     * @param toolId 工具 ID（可能不带前缀）
     * @return 完整的工具 ID，如果未找到返回原 toolId
     */
    private String findFullToolId(String toolId) {
        if (toolId == null) {
            return null;
        }

        if (projectToolDefinitions.containsKey(toolId)
                || projectToolBeanNames.containsKey(toolId)) {
            return toolId;
        }

        String suffix = "_" + toolId;
        for (String key : projectToolDefinitions.keySet()) {
            if (key.endsWith(suffix)) {
                return key;
            }
        }
        for (String key : projectToolBeanNames.keySet()) {
            if (key.endsWith(suffix)) {
                return key;
            }
        }
        for (String key : projectToolCallbacks.keySet()) {
            if (key.endsWith(suffix)) {
                return key;
            }
        }

        return toolId;
    }

    /**
     * 获取 Agent 可用的 PROJECT 工具列表
     *
     * @param agentType Agent 类型
     * @return 工具信息列表
     */
    public List<ToolInfo> getToolsForAgent(String agentType) {
        List<AgentToolAccess> accesses = toolAccessMapper.selectEnabledByAgentType(agentType);

        return accesses.stream()
                .filter(access -> "PROJECT".equals(access.getToolCategory()))
                .map(this::toToolInfo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取 Agent 的 PROJECT 工具
     * 支持后缀匹配以兼容数据库中不带前缀的 toolId
     */
    public List<ToolInfo> getProjectToolsForAgent(String agentType) {
        List<AgentToolAccess> accesses = toolAccessMapper.selectByAgentTypeAndCategory(agentType, "PROJECT");

        return accesses.stream()
                .map(access -> {
                    ToolInfo definition = findProjectToolDefinition(access.getToolId());
                    if (definition == null) {
                        return ToolInfo.builder()
                                .toolId(access.getToolId())
                                .toolName(access.getToolName() != null ? access.getToolName() : access.getToolId())
                                .description(access.getToolDescription())
                                .category("PROJECT")
                                .accessMode(access.getAccessMode())
                                .enabled(access.getEnabled())
                                .dailyQuota(access.getDailyQuota())
                                .build();
                    }
                    return mergeToolInfo(definition, access);
                })
                .collect(Collectors.toList());
    }

    /**
     * 转换为 ToolInfo
     * 支持后缀匹配以兼容数据库中不带前缀的 toolId
     */
    private ToolInfo toToolInfo(AgentToolAccess access) {
        ToolInfo definition = findProjectToolDefinition(access.getToolId());
        if (definition != null) {
            return mergeToolInfo(definition, access);
        }

        return ToolInfo.builder()
                .toolId(access.getToolId())
                .toolName(access.getToolName() != null ? access.getToolName() : access.getToolId())
                .description(access.getToolDescription())
                .category(access.getToolCategory())
                .accessMode(access.getAccessMode())
                .enabled(access.getEnabled())
                .dailyQuota(access.getDailyQuota())
                .build();
    }

    /**
     * 合并工具定义和访问配置
     */
    private ToolInfo mergeToolInfo(ToolInfo definition, AgentToolAccess access) {
        ToolInfo merged = copyToolInfo(definition);
        merged.setToolName(access.getToolName() != null ? access.getToolName() : definition.getToolName());
        merged.setDescription(access.getToolDescription() != null ? access.getToolDescription() : definition.getDescription());
        merged.setAccessMode(access.getAccessMode());
        merged.setEnabled(access.getEnabled());
        merged.setDailyQuota(access.getDailyQuota());
        return merged;
    }

    private Map<String, ToolCallback> resolveCallbacksForBean(String beanName) {
        return getProjectToolBeanByBeanName(beanName)
                .map(bean -> {
                    ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                            .toolObjects(bean)
                            .build()
                            .getToolCallbacks();

                    Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();
                    for (ToolCallback callback : callbacks) {
                        callbacksByName.put(callback.getToolDefinition().name(), callback);
                    }
                    log.debug("resolveCallbacksForBean({}): MethodToolCallbackProvider returned {} callback(s): {}",
                            beanName, callbacks.length, callbacksByName.keySet());

                    Set<String> toolIds = projectToolIdsByBeanName.getOrDefault(beanName, Set.of());
                    for (String toolId : toolIds) {
                        ToolInfo definition = projectToolDefinitions.get(toolId);
                        if (definition == null) {
                            log.warn("resolveCallbacksForBean: no definition for toolId={} in bean={}", toolId, beanName);
                            continue;
                        }
                        ToolCallback callback = callbacksByName.get(definition.getToolName());
                        if (callback == null) {
                            log.warn("PROJECT 工具回调未找到: toolId={}, toolName={}, beanName={}, availableNames={}",
                                    toolId, definition.getToolName(), beanName, callbacksByName.keySet());
                            continue;
                        }
                        projectToolCallbacks.put(toolId, callback);
                        enrichWithCallbackMetadata(definition, callback);
                    }
                    return Map.copyOf(callbacksByName);
                })
                .orElseGet(() -> {
                    log.warn("resolveCallbacksForBean: bean '{}' not found in ApplicationContext", beanName);
                    return Map.of();
                });
    }

    private Optional<Object> getProjectToolBeanByBeanName(String beanName) {
        if (beanName == null || beanName.isBlank()) {
            return Optional.empty();
        }

        Object cachedBean = projectToolBeans.get(beanName);
        if (cachedBean != null) {
            return Optional.of(cachedBean);
        }

        try {
            Object resolvedBean = applicationContext.getBean(beanName);
            projectToolBeans.put(beanName, resolvedBean);
            return Optional.of(resolvedBean);
        } catch (Exception e) {
            log.warn("获取 PROJECT 工具 Bean 失败: beanName={}, error={}", beanName, e.getMessage());
            return Optional.empty();
        }
    }

    private void enrichWithCallbackMetadata(ToolInfo toolInfo, ToolCallback callback) {
        if (toolInfo == null || callback == null || callback.getToolDefinition() == null) {
            return;
        }
        toolInfo.setCallbackName(callback.getToolDefinition().name());
        if (toolInfo.getDescription() == null || toolInfo.getDescription().isBlank()) {
            toolInfo.setDescription(callback.getToolDefinition().description());
        }
        if (toolInfo.getSummary() == null || toolInfo.getSummary().isBlank()) {
            toolInfo.setSummary(callback.getToolDefinition().description());
        }
        if (toolInfo.getInputSchema() == null || toolInfo.getInputSchema().isBlank()) {
            toolInfo.setInputSchema(callback.getToolDefinition().inputSchema());
        }
    }

    /**
     * 检查工具是否可用
     */
    public boolean isToolAvailable(String agentType, String toolCategory, String toolId) {
        AgentToolAccess access = toolAccessMapper.selectByAgentAndTool(agentType, toolCategory, toolId);
        if (access == null) {
            return false;
        }
        return Boolean.TRUE.equals(access.getEnabled()) && !"DISABLED".equals(access.getAccessMode());
    }

    private ToolInfo copyToolInfo(ToolInfo source) {
        if (source == null) {
            return null;
        }

        return ToolInfo.builder()
                .toolId(source.getToolId())
                .toolClass(source.getToolClass())
                .toolMethod(source.getToolMethod())
                .toolName(source.getToolName())
                .displayName(source.getDisplayName())
                .description(source.getDescription())
                .summary(source.getSummary())
                .purpose(source.getPurpose())
                .category(source.getCategory())
                .sourceType(source.getSourceType())
                .actionType(source.getActionType())
                .accessMode(source.getAccessMode())
                .callbackName(source.getCallbackName())
                .params(copyParams(source.getParams()))
                .inputSchema(source.getInputSchema())
                .returnType(source.getReturnType())
                .output(copyOutput(source.getOutput()))
                .enabled(source.getEnabled())
                .dailyQuota(source.getDailyQuota())
                .usedToday(source.getUsedToday())
                .available(source.getAvailable())
                .tags(copyList(source.getTags()))
                .usageNotes(copyList(source.getUsageNotes()))
                .errorCases(copyList(source.getErrorCases()))
                .exampleInput(source.getExampleInput())
                .exampleOutput(source.getExampleOutput())
                .skillNames(copyList(source.getSkillNames()))
                .directToolMode(source.getDirectToolMode())
                .metadata(source.getMetadata() != null ? new LinkedHashMap<>(source.getMetadata()) : null)
                .build();
    }

    private List<ToolParam> copyParams(List<ToolParam> params) {
        if (params == null || params.isEmpty()) {
            return params;
        }
        return params.stream()
                .map(param -> ToolParam.builder()
                        .name(param.getName())
                        .type(param.getType())
                        .description(param.getDescription())
                        .required(param.getRequired())
                        .defaultValue(param.getDefaultValue())
                        .example(param.getExample())
                        .enumValues(copyList(param.getEnumValues()))
                        .build())
                .toList();
    }

    private ToolOutput copyOutput(ToolOutput output) {
        if (output == null) {
            return null;
        }
        return ToolOutput.builder()
                .type(output.getType())
                .description(output.getDescription())
                .schemaClass(output.getSchemaClass())
                .schemaJson(output.getSchemaJson())
                .example(output.getExample())
                .build();
    }

    private List<String> copyList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        return new ArrayList<>(source);
    }
}
