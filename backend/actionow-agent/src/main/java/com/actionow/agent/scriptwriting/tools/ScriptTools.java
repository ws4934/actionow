package com.actionow.agent.scriptwriting.tools;

import com.actionow.agent.core.scope.AgentContextHolder;
import com.actionow.agent.core.scope.AgentScope;
import com.actionow.agent.core.scope.ScopeAccessException;
import com.actionow.agent.core.scope.ScopeValidator;
import com.actionow.agent.feign.ProjectFeignClient;
import com.actionow.agent.tool.annotation.AgentToolOutput;
import com.actionow.agent.tool.annotation.AgentToolParamSpec;
import com.actionow.agent.tool.annotation.AgentToolSpec;
import com.actionow.agent.tool.annotation.ToolActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧本工具类（SAA v2）
 * @Schema → @Tool / @ToolParam (Spring AI)
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ScriptTools extends AbstractProjectTool {

    public ScriptTools(ProjectFeignClient projectClient, ScopeValidator scopeValidator) {
        super(projectClient, scopeValidator);
    }

    @Tool(name = "create_script", description = "创建新剧本。剧本是内容创作的顶层容器，包含标题、简介、正文内容等信息。注意：只有在全局对话中才能创建新剧本。")
    @AgentToolSpec(
            displayName = "创建剧本",
            summary = "创建一个新的剧本容器。",
            purpose = "用于在全局对话中初始化新的剧本创作项目。",
            actionType = ToolActionType.WRITE,
            tags = {"script", "creation"},
            usageNotes = {"仅限全局作用域调用", "至少提供剧本标题"},
            errorCases = {"非全局作用域调用会被拒绝", "title 为空时会返回校验错误"},
            exampleInput = "{\"title\":\"星际迷航\",\"synopsis\":\"一支远征队踏上未知星域\",\"content\":\"第一幕...\"}",
            exampleOutput = "{\"success\":true,\"scriptId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"剧本创建成功\"}"
    )
    @AgentToolOutput(
            description = "返回创建后的剧本 ID 与成功消息。",
            example = "{\"success\":true,\"scriptId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"剧本「星际迷航」创建成功\"}"
    )
    public Map<String, Object> createScript(
            @AgentToolParamSpec(example = "星际迷航")
            @ToolParam(description = "剧本标题（必填）") String title,
            @AgentToolParamSpec(example = "一支远征队踏上未知星域")
            @ToolParam(description = "剧本简介", required = false) String synopsis,
            @AgentToolParamSpec(example = "第一幕：远征队收到神秘信号...")
            @ToolParam(description = "剧本正文内容", required = false) String content) {

        Map<String, Object> validationError = validateRequired(title, "剧本标题");
        if (validationError != null) return validationError;

        try {
            scopeValidator.requireGlobalScope();
        } catch (ScopeAccessException e) {
            return handleScopeException(e);
        }

        return execute("创建剧本", () -> {
            Map<String, Object> request = new HashMap<>();
            request.put("title", title);
            addIfNotBlank(request, "synopsis", synopsis);
            addIfNotBlank(request, "content", content);

            return handleResult(projectClient.createScript(request), "创建剧本", data ->
                    success("scriptId", data.get("id"), "剧本「" + title + "」创建成功"));
        });
    }

    @Tool(name = "get_script", description = "获取剧本详细信息，包括标题、描述、状态、创建时间等")
    @AgentToolSpec(
            displayName = "获取剧本详情",
            summary = "按 ID 获取剧本详细信息。",
            purpose = "当你已经知道剧本 ID，或在剧本作用域中需要读取完整剧本内容时使用。",
            actionType = ToolActionType.READ,
            tags = {"script", "detail"},
            usageNotes = {"在剧本作用域中可省略 scriptId", "已知精确 ID 时优先使用本工具而不是 query_scripts"},
            errorCases = {"缺少 scriptId 且当前不在剧本作用域时会返回错误"},
            exampleInput = "{\"scriptId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}",
            exampleOutput = "{\"success\":true,\"script\":{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"title\":\"星际迷航\"}}"
    )
    @AgentToolOutput(
            description = "返回单个剧本详情对象。",
            example = "{\"success\":true,\"script\":{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"title\":\"星际迷航\",\"status\":\"DRAFT\"}}"
    )
    public Map<String, Object> getScript(
            @AgentToolParamSpec(example = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
            @ToolParam(description = "剧本ID（在剧本作用域中可省略，自动使用当前剧本）", required = false) String scriptId) {

        String resolvedScriptId = resolveScriptId(scriptId);
        if (resolvedScriptId == null) {
            return error("请指定剧本ID，或在剧本对话中使用此功能");
        }

        try {
            scopeValidator.validateScriptAccess(resolvedScriptId);
        } catch (ScopeAccessException e) {
            return handleScopeException(e);
        }

        return execute("获取剧本", () ->
                handleResult(projectClient.getScript(resolvedScriptId), "获取剧本", data -> successData("script", data)));
    }

    @Tool(name = "update_script", description = "更新剧本信息，可以修改标题、简介、内容、状态等")
    @AgentToolSpec(
            displayName = "更新剧本",
            summary = "更新剧本标题、简介、正文或状态。",
            purpose = "用于在已存在剧本上保存创作修改，并支持版本化保存策略。",
            actionType = ToolActionType.WRITE,
            tags = {"script", "update", "versioning"},
            usageNotes = {"在剧本作用域中可省略 scriptId", "推荐使用 NEW_VERSION 保留历史版本"},
            errorCases = {"缺少 scriptId 且当前不在剧本作用域时会返回错误", "没有任何更新字段时会返回错误"},
            exampleInput = "{\"scriptId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"title\":\"星际迷航：序章\",\"saveMode\":\"NEW_VERSION\"}",
            exampleOutput = "{\"success\":true,\"scriptId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"剧本更新成功\"}"
    )
    @AgentToolOutput(
            description = "返回更新后的剧本 ID 与成功消息。",
            example = "{\"success\":true,\"scriptId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"剧本更新成功\"}"
    )
    public Map<String, Object> updateScript(
            @AgentToolParamSpec(example = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
            @ToolParam(description = "剧本ID（在剧本作用域中可省略，自动使用当前剧本）", required = false) String scriptId,
            @ToolParam(description = "新标题", required = false) String title,
            @ToolParam(description = "新简介", required = false) String synopsis,
            @ToolParam(description = "新正文内容", required = false) String content,
            @AgentToolParamSpec(enumValues = {"DRAFT", "IN_PROGRESS", "COMPLETED", "ARCHIVED"})
            @ToolParam(description = "状态: DRAFT(草稿), IN_PROGRESS(进行中), COMPLETED(已完成), ARCHIVED(已归档)", required = false) String status,
            @AgentToolParamSpec(defaultValue = "NEW_VERSION", enumValues = {"OVERWRITE", "NEW_VERSION", "NEW_ENTITY"})
            @ToolParam(description = "保存模式: OVERWRITE(覆盖当前版本), NEW_VERSION(存为新版本，默认), NEW_ENTITY(另存为新实体)", required = false) String saveMode) {

        String resolvedScriptId = resolveScriptId(scriptId);
        if (resolvedScriptId == null) {
            return error("请指定剧本ID，或在剧本对话中使用此功能");
        }

        try {
            scopeValidator.validateScriptAccess(resolvedScriptId);
        } catch (ScopeAccessException e) {
            return handleScopeException(e);
        }

        return execute("更新剧本", () -> {
            Map<String, Object> request = new HashMap<>();
            addIfNotBlank(request, "title", title);
            addIfNotBlank(request, "synopsis", synopsis);
            addIfNotBlank(request, "content", content);
            addIfNotBlank(request, "status", status);
            addIfNotBlank(request, "saveMode", saveMode);

            if (request.isEmpty()) {
                return error("没有需要更新的字段");
            }

            return handleResult(projectClient.updateScript(resolvedScriptId, request), "更新剧本", data ->
                    success("scriptId", resolvedScriptId, "剧本更新成功"));
        });
    }

    @Tool(name = "list_scripts", description = "获取剧本列表（轻量）。全局对话中返回所有剧本；剧本/章节/分镜对话中仅返回当前剧本。如需按条件搜索请用 query_scripts。")
    @AgentToolSpec(
            displayName = "轻量列出剧本",
            summary = "快速返回当前上下文可见的剧本列表。",
            purpose = "用于轻量浏览当前作用域内的剧本，而不是做复杂搜索。",
            actionType = ToolActionType.SEARCH,
            tags = {"script", "list"},
            usageNotes = {"全局作用域返回全部剧本", "非全局作用域仅返回当前锚定剧本", "复杂检索请改用 query_scripts"},
            errorCases = {"当前作用域未绑定剧本时会返回空列表而不是错误"},
            exampleInput = "{}",
            exampleOutput = "{\"success\":true,\"scripts\":[{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"title\":\"星际迷航\"}],\"count\":1}"
    )
    @AgentToolOutput(
            description = "返回轻量剧本列表及作用域提示信息。",
            example = "{\"success\":true,\"scripts\":[{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"title\":\"星际迷航\"}],\"count\":1,\"scope\":\"script\"}"
    )
    public Map<String, Object> listScripts() {
        return execute("获取剧本列表", () -> {
            AgentScope scope = AgentContextHolder.getScope();

            if (scope == AgentScope.GLOBAL) {
                var result = projectClient.listScripts();
                if (result.isSuccess()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("scripts", result.getData());
                    response.put("count", result.getData().size());
                    response.put("scope", "global");
                    return response;
                } else {
                    return error("获取剧本列表失败: " + result.getMessage());
                }
            } else {
                String currentScriptId = getCurrentScriptId();
                if (currentScriptId == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("scripts", List.of());
                    response.put("count", 0);
                    response.put("scope", scope.getCode());
                    response.put("hint", "当前作用域未关联剧本");
                    return response;
                }

                var result = projectClient.getScript(currentScriptId);
                if (result.isSuccess()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("scripts", List.of(result.getData()));
                    response.put("count", 1);
                    response.put("scope", scope.getCode());
                    response.put("hint", "当前对话仅限于剧本「" + currentScriptId + "」范围");
                    return response;
                } else {
                    return error("获取剧本失败: " + result.getMessage());
                }
            }
        });
    }

    @Tool(name = "query_scripts", description = "搜索剧本（可搜索列表）。支持按关键字模糊搜索标题、简介、正文内容、附加信息等，返回分页结果。" +
            "如果你知道剧本ID请用 get_script；如果只需快速浏览请用 list_scripts。")
    @AgentToolSpec(
            displayName = "搜索剧本",
            summary = "按关键字、状态和分页条件搜索剧本。",
            purpose = "当你不知道精确剧本 ID，需要在多个剧本中定位目标剧本时使用。",
            actionType = ToolActionType.SEARCH,
            tags = {"script", "query", "search"},
            usageNotes = {"已知 scriptId 时优先用 get_script", "只需快速浏览时优先用 list_scripts"},
            errorCases = {"分页参数非法时由下游接口返回错误"},
            exampleInput = "{\"keyword\":\"星际\",\"status\":\"DRAFT\",\"pageNum\":1,\"pageSize\":20}",
            exampleOutput = "{\"success\":true,\"data\":{\"records\":[]},\"message\":\"搜索剧本成功\"}"
    )
    @AgentToolOutput(
            description = "返回分页剧本搜索结果。",
            example = "{\"success\":true,\"data\":{\"records\":[{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"title\":\"星际迷航\"}]},\"message\":\"搜索剧本成功\"}"
    )
    public Map<String, Object> queryScripts(
            @ToolParam(description = "搜索关键字，匹配标题/简介/正文/附加信息", required = false) String keyword,
            @AgentToolParamSpec(enumValues = {"DRAFT", "IN_PROGRESS", "COMPLETED", "ARCHIVED"})
            @ToolParam(description = "状态过滤: DRAFT/IN_PROGRESS/COMPLETED/ARCHIVED", required = false) String status,
            @AgentToolParamSpec(defaultValue = "1", example = "1")
            @ToolParam(description = "页码，默认1", required = false) Integer pageNum,
            @AgentToolParamSpec(defaultValue = "20", example = "20")
            @ToolParam(description = "每页数量，默认20", required = false) Integer pageSize,
            @AgentToolParamSpec(defaultValue = "created_at", enumValues = {"title", "created_at", "updated_at"})
            @ToolParam(description = "排序字段: title/created_at/updated_at，默认created_at", required = false) String orderBy,
            @AgentToolParamSpec(defaultValue = "desc", enumValues = {"asc", "desc"})
            @ToolParam(description = "排序方向: asc/desc，默认desc", required = false) String orderDir) {

        return execute("搜索剧本", () ->
                handleResult(projectClient.queryScripts(keyword, status, pageNum, pageSize, orderBy, orderDir),
                        "搜索剧本", data -> {
                            Map<String, Object> response = new HashMap<>();
                            response.put("success", true);
                            response.put("data", data);
                            response.put("message", "搜索剧本成功");
                            return response;
                        }));
    }
}
