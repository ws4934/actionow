package com.actionow.agent.interaction;

import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.tool.annotation.AgentToolParamSpec;
import com.actionow.agent.tool.annotation.AgentToolSpec;
import com.actionow.agent.tool.annotation.ChatDirectTool;
import com.actionow.agent.tool.annotation.MissionDirectTool;
import com.actionow.agent.tool.annotation.ToolActionType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Human-in-the-Loop 工具集。
 *
 * <p>Agent 在遇到不确定、需要用户选择、需要用户确认、需要用户自由输入的场景时调用这些工具。
 * 工具会向当前 SSE 流推送 {@code ask_user} 事件（前端渲染为弹窗），然后**阻塞当前工具线程**
 * 等待用户从 UI 提交答案（经 {@code POST /agent/sessions/{sessionId}/ask/{askId}/answer}），
 * 把答案作为工具返回值回传给 LLM，LLM 据此继续推理。
 *
 * <p>超时 / 用户拒绝 / session 被取消 都会作为常规工具结果返回，不会让 agent 崩溃；
 * 返回载荷里 {@code success} 和 {@code status} 字段明确告知 LLM 当前状况。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AskUserTools {

    private static final int DEFAULT_TIMEOUT_SEC = 180;
    private static final int MAX_TIMEOUT_SEC = 600;

    private final UserInteractionService interaction;
    private final AgentStreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final AgentAskHistoryService askHistoryService;

    // ==================== single_choice ====================

    @ChatDirectTool
    @MissionDirectTool
    @Tool(name = "ask_user_choice", description = "向用户弹出可视化选项并暂停执行等待选择。"
            + "当 Agent 遇到不确定的分支、需要让用户在几个明确方案中挑一个时使用。"
            + "绝不要在你自己可以独立决策的场景里调用此工具。"
            + "choicesJson 为 JSON 数组字符串，每项 {id, label, description?}。"
            + "返回值里的 answer 字段是用户选中的 choice id；如用户拒绝或超时，status 字段会说明。")
    @AgentToolSpec(
            displayName = "向用户询问选择",
            summary = "暂停执行，向用户展示选项并等待其选择。",
            purpose = "在多个合理方案间需要用户拍板的场景下获取确定性答案。",
            actionType = ToolActionType.CONTROL,
            tags = {"hitl", "interaction"},
            usageNotes = {"仅在确实需要用户决策时使用，不要用于可自行判断的场景",
                    "超时默认 180 秒，可通过 timeoutSec 覆盖（上限 600 秒）",
                    "用户可能拒绝/超时，必须检查返回值的 status 字段再决定后续动作"},
            errorCases = {"无活跃会话时会返回 success=false",
                    "choicesJson 解析失败时返回错误",
                    "超时未收到答案返回 status=TIMEOUT"},
            exampleInput = "{\"question\":\"保留哪个版本？\",\"choicesJson\":\"[{\\\"id\\\":\\\"v1\\\",\\\"label\\\":\\\"当前版本\\\"},{\\\"id\\\":\\\"v2\\\",\\\"label\\\":\\\"新版本\\\"}]\"}",
            exampleOutput = "{\"success\":true,\"status\":\"ANSWERED\",\"answer\":\"v2\",\"askId\":\"ask-xxx\"}"
    )
    public Map<String, Object> askUserChoice(
            @ToolParam(description = "面向用户的问题文本（必填）") String question,
            @ToolParam(description = "选项 JSON 数组字符串（必填），例如："
                    + "[{\"id\":\"v1\",\"label\":\"保留当前版本\"},{\"id\":\"v2\",\"label\":\"采用新版本\",\"description\":\"将覆盖旧正文\"}]")
            String choicesJson,
            @AgentToolParamSpec(defaultValue = "180")
            @ToolParam(description = "超时秒数（可选，默认 180，上限 600）", required = false) Integer timeoutSec) {
        List<Map<String, Object>> choices = parseChoices(choicesJson);
        if (choices == null) return invalidArg("choicesJson 解析失败或为空");
        return executeAsk(question, choices, "single_choice", null, timeoutSec);
    }

    // ==================== confirm ====================

    @ChatDirectTool
    @MissionDirectTool
    @Tool(name = "ask_user_confirm", description = "向用户弹出是/否确认框并暂停执行等待答复。"
            + "用于破坏性操作（覆盖、删除）或需要用户明确同意才能继续的场景。"
            + "返回值里 answer 为 'yes' 或 'no'；拒绝/超时通过 status 字段体现。")
    @AgentToolSpec(
            displayName = "向用户确认",
            summary = "暂停执行，向用户请求是/否确认。",
            purpose = "在执行破坏性或不可回退的操作前获得用户明确同意。",
            actionType = ToolActionType.CONTROL,
            tags = {"hitl", "interaction", "confirm"},
            usageNotes = {"破坏性操作（覆盖已有正文、删除实体等）前必须先用此工具获取确认",
                    "超时默认 120 秒，可通过 timeoutSec 覆盖"},
            errorCases = {"无活跃会话时返回 success=false", "超时返回 status=TIMEOUT"},
            exampleInput = "{\"question\":\"此操作会覆盖已有正文，是否继续？\"}",
            exampleOutput = "{\"success\":true,\"status\":\"ANSWERED\",\"answer\":\"yes\",\"askId\":\"ask-xxx\"}"
    )
    public Map<String, Object> askUserConfirm(
            @ToolParam(description = "面向用户的确认问题（必填）") String question,
            @AgentToolParamSpec(defaultValue = "120")
            @ToolParam(description = "超时秒数（可选，默认 120，上限 600）", required = false) Integer timeoutSec) {
        List<Map<String, Object>> choices = List.of(
                Map.of("id", "yes", "label", "是"),
                Map.of("id", "no", "label", "否"));
        return executeAsk(question, choices, "confirm", null, timeoutSec != null ? timeoutSec : 120);
    }

    // ==================== multi_choice ====================

    @ChatDirectTool
    @MissionDirectTool
    @Tool(name = "ask_user_multi_choice", description = "向用户弹出多选框（可勾选多项）并暂停执行等待选择。"
            + "当 Agent 需要用户从候选集中挑若干项时使用，如选择要批量删除的条目、要应用的若干标签等。"
            + "choicesJson 为 JSON 数组字符串，每项 {id, label, description?}。"
            + "minSelect / maxSelect 控制最少 / 最多勾选数（前端强制，后端再校验）。"
            + "返回值里 multiAnswer 字段是被选中的 choice id 列表。")
    @AgentToolSpec(
            displayName = "向用户询问多选",
            summary = "暂停执行，向用户展示多选选项并等待其选择。",
            purpose = "在需要用户从候选集中挑若干项的场景下获取一组答案。",
            actionType = ToolActionType.CONTROL,
            tags = {"hitl", "interaction", "multi"},
            usageNotes = {"若只需单选请改用 ask_user_choice，避免 UI 歧义",
                    "minSelect / maxSelect 越界时返回 status=INVALID_ANSWER，由 agent 决定重试或终止"},
            errorCases = {"choicesJson 解析失败时返回错误",
                    "用户选择数量不在 [minSelect, maxSelect] 范围内返回 status=INVALID_ANSWER"},
            exampleInput = "{\"question\":\"选择要应用的标签\",\"choicesJson\":\"[{\\\"id\\\":\\\"t1\\\",\\\"label\\\":\\\"紧急\\\"},{\\\"id\\\":\\\"t2\\\",\\\"label\\\":\\\"待跟进\\\"}]\",\"minSelect\":1}",
            exampleOutput = "{\"success\":true,\"status\":\"ANSWERED\",\"multiAnswer\":[\"t1\"],\"askId\":\"ask-xxx\"}"
    )
    public Map<String, Object> askUserMultiChoice(
            @ToolParam(description = "面向用户的问题文本（必填）") String question,
            @ToolParam(description = "选项 JSON 数组字符串（必填），结构同 ask_user_choice") String choicesJson,
            @ToolParam(description = "最少勾选数量（可选，默认 1）", required = false) Integer minSelect,
            @ToolParam(description = "最多勾选数量（可选，默认不限）", required = false) Integer maxSelect,
            @AgentToolParamSpec(defaultValue = "180")
            @ToolParam(description = "超时秒数（可选，默认 180，上限 600）", required = false) Integer timeoutSec) {
        List<Map<String, Object>> choices = parseChoices(choicesJson);
        if (choices == null) return invalidArg("choicesJson 解析失败或为空");
        Map<String, Object> constraints = new HashMap<>();
        if (minSelect != null) constraints.put("minSelect", minSelect);
        if (maxSelect != null) constraints.put("maxSelect", maxSelect);
        return executeAsk(question, choices, "multi_choice", constraints, timeoutSec);
    }

    // ==================== text ====================

    @ChatDirectTool
    @MissionDirectTool
    @Tool(name = "ask_user_text", description = "向用户弹出文本输入框并暂停执行等待输入。"
            + "当 Agent 需要用户自由描述（例如补充上下文、提供缺失字段）时使用。"
            + "可选限制 minLength / maxLength，超出范围时返回 status=INVALID_ANSWER。"
            + "返回值里 answer 字段是用户输入的文本。")
    @AgentToolSpec(
            displayName = "向用户请求文本输入",
            summary = "暂停执行，向用户请求自由文本答复。",
            purpose = "在需要用户补充自然语言描述或缺失字段的场景下获取开放式答案。",
            actionType = ToolActionType.CONTROL,
            tags = {"hitl", "interaction", "text"},
            usageNotes = {"若答案是从有限候选里选一个，请用 ask_user_choice 而不是本工具",
                    "maxLength 建议根据业务合理设置，过大影响 UI 体验"},
            errorCases = {"用户返回空白 / 超出 minLength-maxLength 约束时返回 status=INVALID_ANSWER"},
            exampleInput = "{\"question\":\"请用一句话描述这个角色的核心动机\",\"maxLength\":200}",
            exampleOutput = "{\"success\":true,\"status\":\"ANSWERED\",\"answer\":\"渴望被家族认可\",\"askId\":\"ask-xxx\"}"
    )
    public Map<String, Object> askUserText(
            @ToolParam(description = "面向用户的问题文本（必填）") String question,
            @ToolParam(description = "最少字符数（可选，默认 1）", required = false) Integer minLength,
            @ToolParam(description = "最多字符数（可选，默认不限）", required = false) Integer maxLength,
            @AgentToolParamSpec(defaultValue = "180")
            @ToolParam(description = "超时秒数（可选，默认 180，上限 600）", required = false) Integer timeoutSec) {
        Map<String, Object> constraints = new HashMap<>();
        if (minLength != null) constraints.put("minLength", minLength);
        if (maxLength != null) constraints.put("maxLength", maxLength);
        return executeAsk(question, null, "text", constraints, timeoutSec);
    }

    // ==================== number ====================

    @ChatDirectTool
    @MissionDirectTool
    @Tool(name = "ask_user_number", description = "向用户弹出数字输入框并暂停执行等待输入。"
            + "当 Agent 需要用户提供一个数字（例如数量、期限、权重）时使用。"
            + "可选限制 min / max，超出范围或解析失败返回 status=INVALID_ANSWER。"
            + "返回值里 answer 字段是数字原样字符串，number 字段是已解析的 Double。")
    @AgentToolSpec(
            displayName = "向用户请求数字输入",
            summary = "暂停执行，向用户请求数字答复。",
            purpose = "在需要用户提供数量 / 期限 / 权重等数值的场景下获取数字答案。",
            actionType = ToolActionType.CONTROL,
            tags = {"hitl", "interaction", "number"},
            usageNotes = {"如数字必须是整数，请在 question 中明说并在 min/max 里设置整数边界",
                    "解析失败或超出 min/max 时返回 INVALID_ANSWER，由 agent 决定重试或终止"},
            errorCases = {"用户输入非数字返回 status=INVALID_ANSWER",
                    "数字超出 [min, max] 返回 status=INVALID_ANSWER"},
            exampleInput = "{\"question\":\"本次生成几集？\",\"min\":1,\"max\":12}",
            exampleOutput = "{\"success\":true,\"status\":\"ANSWERED\",\"answer\":\"8\",\"number\":8.0,\"askId\":\"ask-xxx\"}"
    )
    public Map<String, Object> askUserNumber(
            @ToolParam(description = "面向用户的问题文本（必填）") String question,
            @ToolParam(description = "最小值（可选）", required = false) Double min,
            @ToolParam(description = "最大值（可选）", required = false) Double max,
            @AgentToolParamSpec(defaultValue = "180")
            @ToolParam(description = "超时秒数（可选，默认 180，上限 600）", required = false) Integer timeoutSec) {
        Map<String, Object> constraints = new HashMap<>();
        if (min != null) constraints.put("min", min);
        if (max != null) constraints.put("max", max);
        return executeAsk(question, null, "number", constraints, timeoutSec);
    }

    // ==================== 内部实现 ====================

    private Map<String, Object> executeAsk(String question,
                                           List<Map<String, Object>> choices,
                                           String inputType,
                                           Map<String, Object> constraints,
                                           Integer timeoutSec) {
        String sessionId = SessionContextHolder.getCurrentSessionId();
        if (sessionId == null) {
            return Map.of("success", false, "status", "NO_SESSION",
                    "error", "无当前会话上下文，无法发起用户询问（本工具必须在 chat 或 mission 执行中使用）");
        }
        if (question == null || question.isBlank()) {
            return invalidArg("question 不能为空");
        }

        int effectiveTimeout = resolveTimeout(timeoutSec);
        String askId = interaction.newAskId();
        long deadlineMs = effectiveTimeout * 1000L;

        MDC.put("askId", askId);
        MDC.put("sessionId", sessionId);
        try {
            askHistoryService.recordPending(sessionId, askId, question, inputType, choices, constraints, effectiveTimeout);

            AgentStreamEvent askEvent = AgentStreamEvent.askUser(askId, question, choices, inputType, deadlineMs);
            if (constraints != null && !constraints.isEmpty()) {
                askEvent.getMetadata().put("constraints", constraints);
            }
            streamBridge.publish(sessionId, askEvent);
            log.info("HITL ask 已推送 SSE: inputType={}, timeoutSec={}", inputType, effectiveTimeout);

            try {
                // 终态的审计持久化由 UserInteractionService 在 submit / cancel / timeout 路径上
                // 统一写入，AskUserTools 只负责工具返回值的 shape。
                UserAnswer ans = interaction.awaitAnswer(sessionId, askId, Duration.ofSeconds(effectiveTimeout));
                if (Boolean.TRUE.equals(ans.getRejected())) {
                    return toolResult(false, "REJECTED", askId,
                            Map.of("message", "用户拒绝回答此问题，请根据情况调整或结束任务"));
                }
                return validateAndShape(inputType, choices, constraints, askId, ans);
            } catch (TimeoutException te) {
                return toolResult(false, "TIMEOUT", askId,
                        Map.of("message", "等待用户回答超时（" + effectiveTimeout + "s），请选择一个合理的默认值继续或终止任务"));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return toolResult(false, "INTERRUPTED", askId,
                        Map.of("message", "等待被中断，任务可能已被取消"));
            } catch (Exception e) {
                log.warn("HITL ask 异常: {}", e.getMessage());
                return toolResult(false, "ERROR", askId, Map.of("error", e.getMessage()));
            }
        } finally {
            MDC.remove("askId");
            MDC.remove("sessionId");
        }
    }

    /**
     * 服务端兜底校验答案是否满足 inputType + constraints，防止前端绕过或旧客户端发送非法数据。
     */
    private Map<String, Object> validateAndShape(String inputType,
                                                 List<Map<String, Object>> choices,
                                                 Map<String, Object> constraints,
                                                 String askId, UserAnswer ans) {
        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("status", "ANSWERED");
        out.put("askId", askId);

        switch (inputType) {
            case "text" -> {
                String v = ans.getAnswer();
                if (v == null) return invalidAnswer(askId, "text 答案为空");
                Integer minLen = asInt(constraints, "minLength");
                Integer maxLen = asInt(constraints, "maxLength");
                int effectiveMin = minLen != null ? minLen : 1;
                if (v.length() < effectiveMin) return invalidAnswer(askId,
                        "文本长度 " + v.length() + " 小于要求的 " + effectiveMin);
                if (maxLen != null && v.length() > maxLen) return invalidAnswer(askId,
                        "文本长度 " + v.length() + " 超过上限 " + maxLen);
                out.put("answer", v);
            }
            case "number" -> {
                String v = ans.getAnswer();
                if (v == null || v.isBlank()) return invalidAnswer(askId, "number 答案为空");
                double parsed;
                try {
                    parsed = Double.parseDouble(v.trim());
                } catch (NumberFormatException nfe) {
                    return invalidAnswer(askId, "无法解析为数字: " + v);
                }
                Double min = asDouble(constraints, "min");
                Double max = asDouble(constraints, "max");
                if (min != null && parsed < min) return invalidAnswer(askId,
                        "数字 " + parsed + " 小于下限 " + min);
                if (max != null && parsed > max) return invalidAnswer(askId,
                        "数字 " + parsed + " 大于上限 " + max);
                out.put("answer", v);
                out.put("number", parsed);
            }
            case "multi_choice" -> {
                List<String> picks = ans.getMultiAnswer();
                if (picks == null || picks.isEmpty()) return invalidAnswer(askId, "未选择任何选项");
                Integer minSel = asInt(constraints, "minSelect");
                Integer maxSel = asInt(constraints, "maxSelect");
                int effectiveMin = minSel != null ? minSel : 1;
                if (picks.size() < effectiveMin) return invalidAnswer(askId,
                        "选中数量 " + picks.size() + " 少于要求 " + effectiveMin);
                if (maxSel != null && picks.size() > maxSel) return invalidAnswer(askId,
                        "选中数量 " + picks.size() + " 超过上限 " + maxSel);
                if (choices != null) {
                    java.util.Set<String> valid = new java.util.HashSet<>();
                    for (Map<String, Object> c : choices) valid.add(String.valueOf(c.get("id")));
                    for (String p : picks) {
                        if (!valid.contains(p)) return invalidAnswer(askId,
                                "选项 id \"" + p + "\" 不在候选集内");
                    }
                }
                out.put("multiAnswer", picks);
            }
            default -> {
                // single_choice / confirm：保持既有行为
                if (ans.getAnswer() != null) out.put("answer", ans.getAnswer());
                if (ans.getMultiAnswer() != null) out.put("multiAnswer", ans.getMultiAnswer());
            }
        }
        if (ans.getExtras() != null) out.put("extras", ans.getExtras());
        return out;
    }

    private List<Map<String, Object>> parseChoices(String choicesJson) {
        if (choicesJson == null || choicesJson.isBlank()) return null;
        try {
            List<Map<String, Object>> list = objectMapper.readValue(choicesJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            return list.isEmpty() ? null : list;
        } catch (Exception e) {
            log.warn("AskUserTools 解析 choicesJson 失败: {}", e.getMessage());
            return null;
        }
    }

    private int resolveTimeout(Integer timeoutSec) {
        if (timeoutSec == null || timeoutSec <= 0) return DEFAULT_TIMEOUT_SEC;
        return Math.min(timeoutSec, MAX_TIMEOUT_SEC);
    }

    private Map<String, Object> invalidArg(String message) {
        return Map.of("success", false, "status", "INVALID_ARG", "error", message);
    }

    private Map<String, Object> invalidAnswer(String askId, String message) {
        return toolResult(false, "INVALID_ANSWER", askId, Map.of("error", message));
    }

    private Map<String, Object> toolResult(boolean success, String status, String askId,
                                           Map<String, Object> extras) {
        Map<String, Object> out = new HashMap<>();
        out.put("success", success);
        out.put("status", status);
        out.put("askId", askId);
        if (extras != null) out.putAll(extras);
        return out;
    }

    private Integer asInt(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private Double asDouble(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }
}
