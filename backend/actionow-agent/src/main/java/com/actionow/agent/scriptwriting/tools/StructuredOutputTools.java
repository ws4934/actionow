package com.actionow.agent.scriptwriting.tools;

import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.interaction.AgentStreamBridge;
import com.actionow.agent.metrics.AgentMetrics;
import com.actionow.agent.registry.DatabaseSkillRegistry;
import com.actionow.agent.saa.session.SaaSessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通用结构化输出工具
 * 替代 {@link PromptOutputTools} 静态 Map，使用 Caffeine 本地缓存（TTL 30min，max 1000）。
 *
 * <p>当 Skill 配置了 {@code outputSchema} 时，Agent 在任务完成后必须调用此工具提交结构化结果。
 * 若 Skill 未配置 Schema，直接在消息中输出内容，无需调用此工具。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredOutputTools {

    private final DatabaseSkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;
    private final AgentStreamBridge streamBridge;
    private final SaaSessionService sessionService;
    private final AgentMetrics metrics;

    /**
     * 结果缓存：Key = sessionId，Value = 已验证的 JSON 结果 Map
     */
    private static final Cache<String, Map<String, Object>> RESULT_CACHE =
            Caffeine.newBuilder()
                    .expireAfterWrite(30, TimeUnit.MINUTES)
                    .maximumSize(1000)
                    .build();

    @Tool(
            name = "output_structured_result",
            description = "当技能任务完成且配置了outputSchema时，通过此工具提交最终JSON结果。" +
                          "若技能未配置Schema，直接在消息中输出内容，无需调用此工具。"
    )
    public Map<String, Object> outputStructuredResult(
            @ToolParam(description = "当前激活的技能名称，如 character_expert") String skillName,
            @ToolParam(description = "符合技能outputSchema的JSON字符串") String jsonOutput) {

        if (jsonOutput == null || jsonOutput.isBlank()) {
            return Map.of("success", false, "error", "jsonOutput 不能为空");
        }

        // 1. 解析 JSON
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(jsonOutput, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("output_structured_result: JSON 解析失败, skillName={}: {}", skillName, e.getMessage());
            return Map.of("success", false, "error", "JSON 解析失败: " + e.getMessage());
        }

        // 2. 若 outputSchema 非空，校验 required 字段和类型（返回错误 Map，不抛异常，避免 Agent 进入异常循环）
        var schemaOpt = skillRegistry.getOutputSchema(skillName);
        if (schemaOpt.isPresent()) {
            Map<String, Object> schema = schemaOpt.get();

            // 2a. 校验 required 字段存在性
            Object requiredObj = schema.get("required");
            if (requiredObj instanceof List<?> required) {
                List<String> missingFields = new java.util.ArrayList<>();
                for (Object field : required) {
                    String fieldName = String.valueOf(field);
                    if (!result.containsKey(fieldName)) {
                        missingFields.add(fieldName);
                    }
                }
                if (!missingFields.isEmpty()) {
                    String msg = "结果缺少必填字段: " + String.join(", ", missingFields) + "（技能 " + skillName + " 的 outputSchema 要求）";
                    log.warn("output_structured_result: {}", msg);
                    return Map.of("success", false, "error", msg);
                }
            }

            // 2b. 校验字段类型（若 schema 定义了 properties）
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                List<String> typeErrors = new java.util.ArrayList<>();
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String fieldName = entry.getKey();
                    if (!result.containsKey(fieldName)) continue;

                    Object fieldValue = result.get(fieldName);
                    if (fieldValue == null) continue;

                    if (entry.getValue() instanceof Map<?, ?> propDef) {
                        String expectedType = String.valueOf(propDef.get("type"));
                        boolean typeValid = switch (expectedType) {
                            case "string" -> fieldValue instanceof String;
                            case "integer", "number" -> fieldValue instanceof Number;
                            case "boolean" -> fieldValue instanceof Boolean;
                            case "array" -> fieldValue instanceof List;
                            case "object" -> fieldValue instanceof Map;
                            default -> true; // 未知类型不校验
                        };
                        if (!typeValid) {
                            typeErrors.add(fieldName + "（期望 " + expectedType + "，实际 " + fieldValue.getClass().getSimpleName() + "）");
                        }

                        // 2c. 校验 enum 值（若定义了 enum）
                        Object enumObj = propDef.get("enum");
                        if (enumObj instanceof List<?> enumValues && fieldValue instanceof String strValue) {
                            boolean found = enumValues.stream().anyMatch(e -> String.valueOf(e).equals(strValue));
                            if (!found) {
                                typeErrors.add(fieldName + "（值 \"" + strValue + "\" 不在允许范围 " + enumValues + " 内）");
                            }
                        }
                    }
                }
                if (!typeErrors.isEmpty()) {
                    String msg = "结果字段类型校验失败: " + String.join("; ", typeErrors);
                    log.warn("output_structured_result: {}", msg);
                    return Map.of("success", false, "error", msg);
                }
            }
        }

        // 3. 存入缓存（key = sessionId）
        String sessionId = resolveSessionId();
        if (sessionId != null) {
            RESULT_CACHE.put(sessionId, result);
            log.debug("output_structured_result: 结果已缓存, sessionId={}, skillName={}", sessionId, skillName);

            // 4. 同步向 SSE 推 structured_data 事件，前端可据 schemaRef 拉 outputSchema 做富渲染
            streamBridge.publish(sessionId, AgentStreamEvent.structuredData(
                    skillName,       // schemaRef 用技能名；前端据此 GET /agent/skills/{name}/output-schema
                    result,
                    resolveRendererHint(skillName)));
            metrics.recordStructuredData();

            // 5. 持久化：保存为 assistant 消息（content=JSON），extras 标记类型 + schemaRef，
            //    供会话重载时前端恢复结构化卡片而不是只看到纯 JSON 文本。
            try {
                Map<String, Object> extras = new java.util.HashMap<>();
                extras.put("type", "STRUCTURED_DATA");
                extras.put("schemaRef", skillName);
                extras.put("renderer", resolveRendererHint(skillName));
                String content = objectMapper.writeValueAsString(result);
                sessionService.saveAssistantMessageWithExtras(sessionId, content, null, extras);
            } catch (Exception persistEx) {
                log.warn("output_structured_result: 持久化失败 sessionId={}: {}", sessionId, persistEx.getMessage());
            }
        } else {
            log.warn("output_structured_result: 无法获取 sessionId，结果未缓存");
        }

        return Map.of("success", true, "skillName", skillName, "fieldCount", result.size());
    }

    /**
     * 按 skillName 推荐前端渲染组件。
     * <p>当前是最小约定（仅 card 默认），未来可以扩展为从 skill metadata 里读 `renderer` 字段。
     */
    private String resolveRendererHint(String skillName) {
        if (skillName == null) return "card";
        // 简单启发：按 skill 名后缀决定组件；保守落 card，前端自行决定降级
        String lower = skillName.toLowerCase();
        if (lower.contains("list") || lower.contains("table")) return "table";
        if (lower.contains("form")) return "form";
        return "card";
    }

    /**
     * 获取并移除会话的结构化结果（由 SaaAgentRunner 在流结束后调用）
     */
    public static Map<String, Object> getAndRemoveResult(String sessionId) {
        if (sessionId == null) return null;
        return RESULT_CACHE.asMap().remove(sessionId);
    }

    /**
     * 清理会话的结构化结果（由 AgentTeardownService 在清理阶段调用，幂等）
     */
    public static void clearResult(String sessionId) {
        if (sessionId != null) {
            RESULT_CACHE.invalidate(sessionId);
        }
    }

    // ==================== 私有方法 ====================

    private String resolveSessionId() {
        String sessionId = SessionContextHolder.getCurrentSessionId();
        if (sessionId != null) return sessionId;
        var ctx = SessionContextHolder.getCurrentContext();
        if (ctx != null && ctx.getAgentContext() != null) {
            return ctx.getAgentContext().getSessionId();
        }
        return null;
    }
}
