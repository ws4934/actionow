package com.actionow.agent.tool.scanner;

import com.actionow.agent.tool.annotation.AgentToolOutput;
import com.actionow.agent.tool.annotation.AgentToolParamSpec;
import com.actionow.agent.tool.annotation.AgentToolSpec;
import com.actionow.agent.tool.annotation.ChatDirectTool;
import com.actionow.agent.tool.annotation.MissionDirectTool;
import com.actionow.agent.tool.annotation.ToolActionType;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.dto.ToolOutput;
import com.actionow.agent.tool.dto.ToolParam;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * PROJECT 工具扫描器（SAA v2）
 * 自动扫描带有 Spring AI @Tool 注解的工具方法，并注册到 UnifiedToolRegistry。
 *
 * <p>注意：这里只扫描 Bean 类型元数据，不主动实例化 Bean，
 * 避免在容器启动阶段把 Controller / Runner 等链路提前拉起，导致循环依赖。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectToolScanner {

    private final ApplicationContext applicationContext;
    private final ProjectToolRegistry toolRegistry;

    /**
     * 需要扫描的工具类包路径前缀列表。
     * <ul>
     *   <li>scriptwriting.tools — 剧本创编域的业务工具（CRUD、多模态、关系）</li>
     *   <li>mission.tools — Mission 生命周期控制工具（创建/完成/失败/委派）</li>
     *   <li>interaction — HITL 交互工具（ask_user_choice / ask_user_confirm 等）</li>
     * </ul>
     */
    private static final List<String> TOOLS_PACKAGE_PREFIXES = List.of(
            "com.actionow.agent.scriptwriting.tools",
            "com.actionow.agent.mission.tools",
            "com.actionow.agent.interaction"
    );

    /**
     * 工具类后缀
     */
    private static final String TOOLS_CLASS_SUFFIX = "Tools";

    @PostConstruct
    public void scanAndRegisterTools() {
        log.info("开始扫描 PROJECT 工具...");

        int registeredCount = 0;

        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) {
                continue;
            }

            Class<?> targetClass = ClassUtils.getUserClass(beanType);
            if (!isToolClass(targetClass)) {
                continue;
            }

            log.debug("发现工具类: {} (beanName: {})", targetClass.getName(), beanName);

            for (Method method : targetClass.getDeclaredMethods()) {
                Tool toolAnnotation = method.getAnnotation(Tool.class);
                if (toolAnnotation == null) {
                    continue;
                }

                String toolId = generateToolId(targetClass, method);
                String toolName = toolAnnotation.name().isEmpty() ? generateToolName(method) : toolAnnotation.name();
                String description = toolAnnotation.description();
                AgentToolSpec toolSpec = method.getAnnotation(AgentToolSpec.class);
                AgentToolOutput outputAnnotation = method.getAnnotation(AgentToolOutput.class);
                List<ToolParam> params = parseParameters(method);
                ToolOutput output = parseOutput(method, outputAnnotation);

                ToolInfo toolInfo = ToolInfo.builder()
                        .toolId(toolId)
                        .toolClass(targetClass.getName())
                        .toolMethod(method.getName())
                        .toolName(toolName)
                        .callbackName(toolName)
                        .displayName(resolveDisplayName(toolSpec, toolName, description))
                        .description(description)
                        .summary(resolveSummary(toolSpec, description))
                        .purpose(resolvePurpose(toolSpec))
                        .category("PROJECT")
                        .sourceType("CODE_SCAN")
                        .actionType(resolveActionType(toolSpec, toolName))
                        .accessMode("FULL")
                        .params(params)
                        .returnType(describeType(method.getGenericReturnType()))
                        .output(output)
                        .tags(resolveTags(toolSpec, targetClass))
                        .usageNotes(resolveList(toolSpec != null ? toolSpec.usageNotes() : null))
                        .errorCases(resolveList(toolSpec != null ? toolSpec.errorCases() : null))
                        .exampleInput(toolSpec != null && !toolSpec.exampleInput().isBlank() ? toolSpec.exampleInput() : null)
                        .exampleOutput(toolSpec != null && !toolSpec.exampleOutput().isBlank() ? toolSpec.exampleOutput() : null)
                        .enabled(true)
                        .dailyQuota(-1)
                        .directToolMode(resolveDirectToolMode(method))
                        .build();

                toolRegistry.registerProjectTool(toolInfo);
                toolRegistry.registerProjectToolBean(toolId, beanName);

                registeredCount++;
                log.debug("注册 PROJECT 工具: {} -> {}.{} (toolName: {}, beanName: {})",
                        toolId, targetClass.getSimpleName(), method.getName(), toolName, beanName);
            }
        }

        log.info("PROJECT 工具扫描完成，共注册 {} 个工具", registeredCount);
    }

    /**
     * 判断是否是工具类
     */
    private boolean isToolClass(Class<?> clazz) {
        String className = clazz.getName();
        boolean inToolsPackage = TOOLS_PACKAGE_PREFIXES.stream().anyMatch(className::startsWith);
        if (!inToolsPackage) {
            return false;
        }

        String simpleName = clazz.getSimpleName();
        return simpleName.endsWith(TOOLS_CLASS_SUFFIX);
    }

    /**
     * 生成工具 ID
     * 格式: {classPrefix}_{methodName}
     * 例如: script_updateScript, character_createCharacter
     * 类前缀从工具类名提取（去掉 Tools 后缀，转小写）
     */
    private String generateToolId(Class<?> targetClass, Method method) {
        String classPrefix = extractClassPrefix(targetClass);
        return classPrefix + "_" + method.getName();
    }

    /**
     * 从类名提取前缀
     * ScriptTools → script
     * CharacterTools → character
     * MultimodalTools → multimodal
     */
    private String extractClassPrefix(Class<?> targetClass) {
        String simpleName = targetClass.getSimpleName();
        if (simpleName.endsWith(TOOLS_CLASS_SUFFIX)) {
            simpleName = simpleName.substring(0, simpleName.length() - TOOLS_CLASS_SUFFIX.length());
        }
        return simpleName.toLowerCase();
    }

    /**
     * 生成工具名称（用于 Agent 调用）
     * 使用方法名，转换为下划线格式
     */
    private String generateToolName(Method method) {
        String methodName = method.getName();
        return camelToSnake(methodName);
    }

    /**
     * 解析方法参数（Spring AI @ToolParam 注解）
     */
    private List<ToolParam> parseParameters(Method method) {
        List<ToolParam> params = new ArrayList<>();

        for (Parameter parameter : method.getParameters()) {
            org.springframework.ai.tool.annotation.ToolParam paramAnnotation =
                    parameter.getAnnotation(org.springframework.ai.tool.annotation.ToolParam.class);
            AgentToolParamSpec paramSpec = parameter.getAnnotation(AgentToolParamSpec.class);

            ToolParam param = ToolParam.builder()
                    .name(parameter.getName())
                    .type(describeType(parameter.getParameterizedType()))
                    .description(paramAnnotation != null ? paramAnnotation.description() : "")
                    .required(paramAnnotation == null || paramAnnotation.required())
                    .defaultValue(paramSpec != null && !paramSpec.defaultValue().isBlank() ? paramSpec.defaultValue() : null)
                    .example(paramSpec != null && !paramSpec.example().isBlank() ? paramSpec.example() : null)
                    .enumValues(resolveList(paramSpec != null ? paramSpec.enumValues() : null))
                    .build();

            params.add(param);
        }

        return params;
    }

    private ToolOutput parseOutput(Method method, AgentToolOutput outputAnnotation) {
        String schemaClass = null;
        if (outputAnnotation != null && outputAnnotation.schemaClass() != Void.class) {
            schemaClass = outputAnnotation.schemaClass().getName();
        }

        return ToolOutput.builder()
                .type(describeType(method.getGenericReturnType()))
                .description(outputAnnotation != null && !outputAnnotation.description().isBlank()
                        ? outputAnnotation.description() : null)
                .schemaClass(schemaClass)
                .schemaJson(outputAnnotation != null && !outputAnnotation.schemaJson().isBlank()
                        ? outputAnnotation.schemaJson() : null)
                .example(outputAnnotation != null && !outputAnnotation.example().isBlank()
                        ? outputAnnotation.example() : null)
                .build();
    }

    private String resolveDisplayName(AgentToolSpec toolSpec, String toolName, String description) {
        if (toolSpec != null && !toolSpec.displayName().isBlank()) {
            return toolSpec.displayName();
        }
        if (description != null && !description.isBlank()) {
            int splitIndex = description.indexOf('。');
            String firstSentence = splitIndex > 0 ? description.substring(0, splitIndex) : description;
            return firstSentence.trim();
        }
        return toolName;
    }

    private String resolveSummary(AgentToolSpec toolSpec, String description) {
        if (toolSpec != null && !toolSpec.summary().isBlank()) {
            return toolSpec.summary();
        }
        return description;
    }

    private String resolvePurpose(AgentToolSpec toolSpec) {
        if (toolSpec != null && !toolSpec.purpose().isBlank()) {
            return toolSpec.purpose();
        }
        return null;
    }

    private List<String> resolveTags(AgentToolSpec toolSpec, Class<?> targetClass) {
        List<String> tags = resolveList(toolSpec != null ? toolSpec.tags() : null);
        if (!tags.isEmpty()) {
            return tags;
        }
        return List.of(extractClassPrefix(targetClass));
    }

    private String resolveActionType(AgentToolSpec toolSpec, String toolName) {
        if (toolSpec != null && toolSpec.actionType() != ToolActionType.UNKNOWN) {
            return toolSpec.actionType().name();
        }
        return inferActionType(toolName).name();
    }

    private ToolActionType inferActionType(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return ToolActionType.UNKNOWN;
        }
        if (toolName.startsWith("get_")) {
            return ToolActionType.READ;
        }
        if (toolName.startsWith("list_") || toolName.startsWith("query_")) {
            return ToolActionType.SEARCH;
        }
        if (toolName.startsWith("generate_")
                || toolName.startsWith("batch_generate_")
                || toolName.startsWith("retry_")) {
            return ToolActionType.GENERATE;
        }
        if (toolName.startsWith("create_mission")
                || toolName.startsWith("update_mission")
                || toolName.startsWith("complete_mission")
                || toolName.startsWith("fail_mission")
                || toolName.startsWith("delegate_")
                || toolName.startsWith("cancel_")) {
            return ToolActionType.CONTROL;
        }
        if (toolName.startsWith("create_")
                || toolName.startsWith("update_")
                || toolName.startsWith("delete_")
                || toolName.startsWith("batch_create_")
                || toolName.startsWith("add_")
                || toolName.startsWith("set_")
                || toolName.startsWith("mount_")
                || toolName.startsWith("unmount_")) {
            return ToolActionType.WRITE;
        }
        return ToolActionType.UNKNOWN;
    }

    private String describeType(Type type) {
        if (type == null) {
            return null;
        }
        return type.getTypeName()
                .replace("java.lang.", "")
                .replace("java.util.", "")
                .replace("java.time.", "");
    }

    private List<String> resolveList(String[] values) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    /**
     * 解析直接工具模式
     * 检查 @MissionDirectTool / @ChatDirectTool 注解
     */
    private String resolveDirectToolMode(Method method) {
        boolean isMission = method.isAnnotationPresent(MissionDirectTool.class);
        boolean isChat = method.isAnnotationPresent(ChatDirectTool.class);
        if (isMission && isChat) {
            return "BOTH";
        }
        if (isMission) {
            return "MISSION";
        }
        if (isChat) {
            return "CHAT";
        }
        return null;
    }

    /**
     * 驼峰转下划线
     */
    private String camelToSnake(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }

        return result.toString();
    }
}
