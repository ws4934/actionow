package com.actionow.agent.scriptwriting.tools;

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
 * 实体关系工具类（SAA v2）
 * 提供实体关系的创建、查询、删除等功能
 * 用于管理分镜与角色、场景、道具、对白之间的关系
 *
 * @author Actionow
 */
@Slf4j
@Component
public class EntityRelationTools extends AbstractProjectTool {

    public EntityRelationTools(ProjectFeignClient projectClient) {
        super(projectClient);
    }

    @Tool(name = "create_relation", description = "创建实体关系（底层工具，非幂等）。优先使用便捷方法: add_character_to_storyboard, set_storyboard_scene, add_prop_to_storyboard, add_dialogue_to_storyboard。" +
            "支持的关系类型: takes_place_in(分镜->场景), appears_in(分镜->角色), uses(分镜->道具), speaks_in(分镜->角色对白), styled_by(实体->风格), character_relationship(角色->角色), equipped_with(角色->道具)")
    @AgentToolSpec(
            displayName = "创建实体关系",
            summary = "底层关系创建工具（非幂等）。",
            purpose = "当便捷方法无法满足需求时使用（如角色间关系 character_relationship、角色装备 equipped_with）。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "creation"},
            usageNotes = {"分镜相关关系优先使用 add_character_to_storyboard / set_storyboard_scene / add_prop_to_storyboard 等便捷方法（幂等）", "此工具非幂等，重复调用会创建多条关系"},
            errorCases = {"必填参数为空时会返回校验错误"},
            exampleInput = "{\"sourceType\":\"CHARACTER\",\"sourceId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx1\",\"targetType\":\"CHARACTER\",\"targetId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx2\",\"relationType\":\"character_relationship\",\"extraInfoJson\":\"{\\\"relationship\\\":\\\"father\\\"}\"}",
            exampleOutput = "{\"success\":true,\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"关系创建成功\"}"
    )
    public Map<String, Object> createRelation(
            @ToolParam(description = "源实体类型: STORYBOARD, CHARACTER, EPISODE 等（必填）") String sourceType,
            @ToolParam(description = "源实体ID（必填）") String sourceId,
            @ToolParam(description = "目标实体类型: SCENE, CHARACTER, PROP, STYLE 等（必填）") String targetType,
            @ToolParam(description = "目标实体ID（必填）") String targetId,
            @ToolParam(description = "关系类型: takes_place_in, appears_in, uses, speaks_in, styled_by, character_relationship, equipped_with（必填）") String relationType,
            @ToolParam(description = "关系元信息JSON，如: {\"position\":\"center\",\"action\":\"standing\",\"expression\":\"happy\"}", required = false) String extraInfoJson,
            @ToolParam(description = "排序序号（可选）", required = false) Integer sequence) {

        Map<String, Object> validationError = validateRequired(sourceType, "源实体类型");
        if (validationError != null) return validationError;
        validationError = validateRequired(sourceId, "源实体ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(targetType, "目标实体类型");
        if (validationError != null) return validationError;
        validationError = validateRequired(targetId, "目标实体ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(relationType, "关系类型");
        if (validationError != null) return validationError;

        return execute("创建实体关系", () -> {
            Map<String, Object> request = new HashMap<>();
            request.put("sourceType", sourceType.toUpperCase());
            request.put("sourceId", sourceId);
            request.put("targetType", targetType.toUpperCase());
            request.put("targetId", targetId);
            request.put("relationType", relationType.toLowerCase());
            addIfNotNull(request, "sequence", sequence);

            if (extraInfoJson != null && !extraInfoJson.isBlank()) {
                try {
                    Map<String, Object> extraInfo = parseJsonObject(extraInfoJson);
                    request.put("extraInfo", extraInfo);
                } catch (Exception e) {
                    log.warn("解析 extraInfo JSON 失败: {}", extraInfoJson);
                }
            }

            return handleResult(projectClient.createEntityRelation(request), "创建关系", data -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("relationId", data.get("id"));
                response.put("message", "关系创建成功");
                return response;
            });
        });
    }

    @Tool(name = "batch_create_relations", description = "批量创建实体关系（非幂等）。适用于一次性为分镜添加多个角色、道具关系。每个元素包含 sourceType, sourceId, targetType, targetId, relationType, extraInfo(可选)。")
    @AgentToolSpec(
            displayName = "批量创建关系",
            summary = "一次性创建多条实体关系。",
            purpose = "用于批量为分镜建立角色、道具、场景关系。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "batch", "creation"},
            usageNotes = {"非幂等操作，重复调用会产生多条关系"},
            errorCases = {"relationsJson 为空时会返回错误"},
            exampleInput = "{\"relationsJson\":\"[{\\\"sourceType\\\":\\\"STORYBOARD\\\",\\\"sourceId\\\":\\\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx1\\\",\\\"targetType\\\":\\\"CHARACTER\\\",\\\"targetId\\\":\\\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx1\\\",\\\"relationType\\\":\\\"appears_in\\\"}]\"}",
            exampleOutput = "{\"success\":true,\"relations\":[{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}],\"count\":1}"
    )
    public Map<String, Object> batchCreateRelations(
            @ToolParam(description = "关系JSON数组，例如: [{\"sourceType\":\"STORYBOARD\",\"sourceId\":\"sb-id\",\"targetType\":\"CHARACTER\",\"targetId\":\"char-id\",\"relationType\":\"appears_in\",\"extraInfo\":{\"position\":\"center\"}}]") String relationsJson) {

        Map<String, Object> validationError = validateRequired(relationsJson, "关系JSON数组");
        if (validationError != null) return validationError;

        return execute("批量创建关系", () -> {
            List<Map<String, Object>> relations = parseJsonArray(relationsJson);
            if (relations.isEmpty()) {
                return error("关系列表不能为空");
            }

            var result = projectClient.batchCreateEntityRelations(relations);
            if (result.isSuccess()) {
                return successList("relations", result.getData());
            }
            return error("批量创建关系失败: " + result.getMessage());
        });
    }

    @Tool(name = "update_relation", description = "更新实体关系的元信息或排序序号。extraInfo 为 merge 语义，不会覆盖未传字段。")
    @AgentToolSpec(
            displayName = "更新关系",
            summary = "更新关系的元信息或排序。",
            purpose = "用于修改关系的位置、动作、表情、排序等属性。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "update"},
            usageNotes = {"至少需要提供 extraInfoJson 或 sequence 其中之一"},
            errorCases = {"relationId 为空时会返回校验错误", "无更新字段时会返回错误"},
            exampleInput = "{\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"extraInfoJson\":\"{\\\"position\\\":\\\"left\\\"}\"}",
            exampleOutput = "{\"success\":true,\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"关系更新成功\"}"
    )
    public Map<String, Object> updateRelation(
            @ToolParam(description = "关系ID（必填）") String relationId,
            @ToolParam(description = "更新的元信息JSON", required = false) String extraInfoJson,
            @ToolParam(description = "更新的排序序号", required = false) Integer sequence) {

        Map<String, Object> validationError = validateRequired(relationId, "关系ID");
        if (validationError != null) return validationError;

        return execute("更新关系", () -> {
            Map<String, Object> request = new HashMap<>();
            addIfNotNull(request, "sequence", sequence);

            if (extraInfoJson != null && !extraInfoJson.isBlank()) {
                try {
                    Map<String, Object> extraInfo = parseJsonObject(extraInfoJson);
                    request.put("extraInfo", extraInfo);
                } catch (Exception e) {
                    log.warn("解析 extraInfo JSON 失败: {}", extraInfoJson);
                }
            }

            if (request.isEmpty()) {
                return error("至少需要提供一个更新字段");
            }

            return handleResult(projectClient.updateEntityRelation(relationId, request), "更新关系", data -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("relationId", data.get("id"));
                response.put("message", "关系更新成功");
                return response;
            });
        });
    }

    @Tool(name = "delete_relation", description = "删除实体关系（按关系ID）。删除后不可恢复，请确认后再执行。")
    @AgentToolSpec(
            displayName = "删除关系",
            summary = "按关系 ID 删除实体关系。",
            purpose = "用于移除不再需要的实体关联。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "delete"},
            usageNotes = {"删除不可恢复"},
            errorCases = {"relationId 为空时会返回校验错误"},
            exampleInput = "{\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}",
            exampleOutput = "{\"success\":true,\"message\":\"关系删除成功\"}"
    )
    public Map<String, Object> deleteRelation(
            @ToolParam(description = "关系ID（必填）") String relationId) {

        Map<String, Object> validationError = validateRequired(relationId, "关系ID");
        if (validationError != null) return validationError;

        return execute("删除关系", () -> {
            var result = projectClient.deleteEntityRelation(relationId);
            if (result.isSuccess()) {
                return Map.of("success", true, "message", "关系删除成功");
            }
            return error("删除关系失败: " + result.getMessage());
        });
    }

    @Tool(name = "list_relations_by_source", description = "查询源实体的所有出向关系。例如查询分镜关联的所有角色、场景、道具。返回所有关系类型的记录。如只需特定类型，使用 list_relations_by_source_and_type。")
    @AgentToolSpec(
            displayName = "查询出向关系",
            summary = "查询源实体的所有出向关系。",
            purpose = "用于了解某个分镜/角色关联了哪些其他实体。",
            actionType = ToolActionType.READ,
            tags = {"relation", "query"},
            usageNotes = {"返回所有类型的关系", "如只需特定类型，使用 list_relations_by_source_and_type"},
            errorCases = {"sourceType 或 sourceId 为空时会返回校验错误"},
            exampleInput = "{\"sourceType\":\"STORYBOARD\",\"sourceId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}",
            exampleOutput = "{\"success\":true,\"relations\":[{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"relationType\":\"appears_in\"}],\"count\":3}"
    )
    public Map<String, Object> listRelationsBySource(
            @ToolParam(description = "源实体类型: STORYBOARD, CHARACTER, EPISODE 等（必填）") String sourceType,
            @ToolParam(description = "源实体ID（必填）") String sourceId) {

        Map<String, Object> validationError = validateRequired(sourceType, "源实体类型");
        if (validationError != null) return validationError;
        validationError = validateRequired(sourceId, "源实体ID");
        if (validationError != null) return validationError;

        return execute("查询源实体关系", () -> {
            var result = projectClient.listRelationsBySource(sourceType.toUpperCase(), sourceId);
            if (result.isSuccess()) {
                return successList("relations", result.getData());
            }
            return error("查询关系失败: " + result.getMessage());
        });
    }

    @Tool(name = "list_relations_by_source_and_type", description = "查询源实体指定类型的关系。例如只查询分镜的角色出现关系(appears_in)或场景关系(takes_place_in)。")
    @AgentToolSpec(
            displayName = "按类型查询出向关系",
            summary = "查询源实体特定类型的关系。",
            purpose = "精确过滤某种关系类型（如只看角色出现 appears_in）。",
            actionType = ToolActionType.READ,
            tags = {"relation", "query", "filter"},
            usageNotes = {"relationType 需精确匹配"},
            errorCases = {"必填参数为空时会返回校验错误"},
            exampleInput = "{\"sourceType\":\"STORYBOARD\",\"sourceId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"relationType\":\"appears_in\"}",
            exampleOutput = "{\"success\":true,\"relations\":[{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"targetId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}],\"count\":2}"
    )
    public Map<String, Object> listRelationsBySourceAndType(
            @ToolParam(description = "源实体类型（必填）") String sourceType,
            @ToolParam(description = "源实体ID（必填）") String sourceId,
            @ToolParam(description = "关系类型: takes_place_in, appears_in, uses, speaks_in, styled_by（必填）") String relationType) {

        Map<String, Object> validationError = validateRequired(sourceType, "源实体类型");
        if (validationError != null) return validationError;
        validationError = validateRequired(sourceId, "源实体ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(relationType, "关系类型");
        if (validationError != null) return validationError;

        return execute("查询源实体指定类型关系", () -> {
            var result = projectClient.listRelationsBySourceAndType(
                    sourceType.toUpperCase(), sourceId, relationType.toLowerCase());
            if (result.isSuccess()) {
                return successList("relations", result.getData());
            }
            return error("查询关系失败: " + result.getMessage());
        });
    }

    @Tool(name = "list_relations_by_target", description = "查询目标实体的入向关系（反向查询）。例如查询某个角色出现在哪些分镜中，某个场景被哪些分镜使用。")
    @AgentToolSpec(
            displayName = "查询入向关系",
            summary = "反向查询目标实体被哪些源实体引用。",
            purpose = "用于影响分析（如了解角色出现在哪些分镜、场景被哪些分镜使用）。",
            actionType = ToolActionType.READ,
            tags = {"relation", "query", "reverse"},
            usageNotes = {"返回指向该目标的所有关系"},
            errorCases = {"targetType 或 targetId 为空时会返回校验错误"},
            exampleInput = "{\"targetType\":\"CHARACTER\",\"targetId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}",
            exampleOutput = "{\"success\":true,\"relations\":[{\"id\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"sourceType\":\"STORYBOARD\",\"sourceId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}],\"count\":5}"
    )
    public Map<String, Object> listRelationsByTarget(
            @ToolParam(description = "目标实体类型: CHARACTER, SCENE, PROP 等（必填）") String targetType,
            @ToolParam(description = "目标实体ID（必填）") String targetId) {

        Map<String, Object> validationError = validateRequired(targetType, "目标实体类型");
        if (validationError != null) return validationError;
        validationError = validateRequired(targetId, "目标实体ID");
        if (validationError != null) return validationError;

        return execute("查询目标实体入向关系", () -> {
            var result = projectClient.listRelationsByTarget(targetType.toUpperCase(), targetId);
            if (result.isSuccess()) {
                return successList("relations", result.getData());
            }
            return error("查询关系失败: " + result.getMessage());
        });
    }

    @Tool(name = "add_character_to_storyboard", description = "为分镜添加角色（便捷方法，幂等操作）。创建分镜与角色的 appears_in 关系，可指定角色在画面中的位置、动作、表情。如果关系已存在则更新而非重复创建。")
    @AgentToolSpec(
            displayName = "添加角色到分镜",
            summary = "幂等地将角色关联到分镜（appears_in）。",
            purpose = "用于为分镜指定出场角色及其画面表现。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "storyboard", "character", "convenience"},
            usageNotes = {"幂等操作：已存在则更新", "优先使用此方法而非底层 create_relation"},
            errorCases = {"storyboardId 或 characterId 为空时会返回校验错误"},
            exampleInput = "{\"storyboardId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"characterId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"position\":\"center\",\"action\":\"standing\",\"expression\":\"happy\"}",
            exampleOutput = "{\"success\":true,\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"角色已添加到分镜\"}"
    )
    public Map<String, Object> addCharacterToStoryboard(
            @ToolParam(description = "分镜ID（必填）") String storyboardId,
            @ToolParam(description = "角色ID（必填）") String characterId,
            @ToolParam(description = "画面位置: left, center, right, background, foreground", required = false) String position,
            @ToolParam(description = "角色动作: standing, sitting, walking, running 等", required = false) String action,
            @ToolParam(description = "表情: happy, sad, angry, surprised, neutral 等", required = false) String expression,
            @ToolParam(description = "排序序号", required = false) Integer sequence) {

        Map<String, Object> validationError = validateRequired(storyboardId, "分镜ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(characterId, "角色ID");
        if (validationError != null) return validationError;

        return execute("添加角色到分镜", () -> {
            Map<String, Object> request = new HashMap<>();
            request.put("sourceType", "STORYBOARD");
            request.put("sourceId", storyboardId);
            request.put("targetType", "CHARACTER");
            request.put("targetId", characterId);
            request.put("relationType", "appears_in");
            addIfNotNull(request, "sequence", sequence);

            Map<String, Object> extraInfo = new HashMap<>();
            addIfNotBlank(extraInfo, "position", position);
            addIfNotBlank(extraInfo, "action", action);
            addIfNotBlank(extraInfo, "expression", expression);
            if (!extraInfo.isEmpty()) {
                request.put("extraInfo", extraInfo);
            }

            return handleResult(projectClient.getOrCreateEntityRelation(request), "添加角色", data -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("relationId", data.get("id"));
                response.put("message", "角色已添加到分镜");
                return response;
            });
        });
    }

    @Tool(name = "set_storyboard_scene", description = "为分镜设置场景（便捷方法，幂等操作）。创建分镜与场景的 takes_place_in 关系，可覆盖场景的时间/天气属性。如果关系已存在则更新而非重复创建。")
    @AgentToolSpec(
            displayName = "设置分镜场景",
            summary = "幂等地将场景关联到分镜（takes_place_in）。",
            purpose = "用于指定分镜发生的场景，可局部覆盖场景的时间/天气。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "storyboard", "scene", "convenience"},
            usageNotes = {"幂等操作：已存在则更新", "timeOfDay/weather 为该分镜对场景属性的局部覆盖"},
            errorCases = {"storyboardId 或 sceneId 为空时会返回校验错误"},
            exampleInput = "{\"storyboardId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"sceneId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"timeOfDay\":\"NIGHT\",\"weather\":\"rainy\"}",
            exampleOutput = "{\"success\":true,\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"场景已设置到分镜\"}"
    )
    public Map<String, Object> setStoryboardScene(
            @ToolParam(description = "分镜ID（必填）") String storyboardId,
            @ToolParam(description = "场景ID（必填）") String sceneId,
            @ToolParam(description = "时间覆盖: DAWN, DAY, DUSK, NIGHT", required = false) String timeOfDay,
            @ToolParam(description = "天气覆盖: sunny, cloudy, rainy, snowy, foggy, stormy", required = false) String weather) {

        Map<String, Object> validationError = validateRequired(storyboardId, "分镜ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(sceneId, "场景ID");
        if (validationError != null) return validationError;

        return execute("设置分镜场景", () -> {
            Map<String, Object> request = new HashMap<>();
            request.put("sourceType", "STORYBOARD");
            request.put("sourceId", storyboardId);
            request.put("targetType", "SCENE");
            request.put("targetId", sceneId);
            request.put("relationType", "takes_place_in");

            Map<String, Object> extraInfo = new HashMap<>();
            addIfNotBlank(extraInfo, "timeOfDay", timeOfDay);
            addIfNotBlank(extraInfo, "weather", weather);
            if (!extraInfo.isEmpty()) {
                request.put("extraInfo", extraInfo);
            }

            return handleResult(projectClient.getOrCreateEntityRelation(request), "设置场景", data -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("relationId", data.get("id"));
                response.put("message", "场景已设置到分镜");
                return response;
            });
        });
    }

    @Tool(name = "add_prop_to_storyboard", description = "为分镜添加道具（便捷方法，幂等操作）。创建分镜与道具的 uses 关系，可指定道具在画面中的位置和交互方式。如果关系已存在则更新而非重复创建。")
    @AgentToolSpec(
            displayName = "添加道具到分镜",
            summary = "幂等地将道具关联到分镜（uses）。",
            purpose = "用于为分镜指定出现的道具及其画面表现。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "storyboard", "prop", "convenience"},
            usageNotes = {"幂等操作：已存在则更新", "优先使用此方法而非底层 create_relation"},
            errorCases = {"storyboardId 或 propId 为空时会返回校验错误"},
            exampleInput = "{\"storyboardId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"propId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"position\":\"foreground\",\"interaction\":\"held\"}",
            exampleOutput = "{\"success\":true,\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"道具已添加到分镜\"}"
    )
    public Map<String, Object> addPropToStoryboard(
            @ToolParam(description = "分镜ID（必填）") String storyboardId,
            @ToolParam(description = "道具ID（必填）") String propId,
            @ToolParam(description = "画面位置: left, center, right, background, foreground", required = false) String position,
            @ToolParam(description = "交互方式: displayed, held, used, hidden 等", required = false) String interaction,
            @ToolParam(description = "排序序号", required = false) Integer sequence) {

        Map<String, Object> validationError = validateRequired(storyboardId, "分镜ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(propId, "道具ID");
        if (validationError != null) return validationError;

        return execute("添加道具到分镜", () -> {
            Map<String, Object> request = new HashMap<>();
            request.put("sourceType", "STORYBOARD");
            request.put("sourceId", storyboardId);
            request.put("targetType", "PROP");
            request.put("targetId", propId);
            request.put("relationType", "uses");
            addIfNotNull(request, "sequence", sequence);

            Map<String, Object> extraInfo = new HashMap<>();
            addIfNotBlank(extraInfo, "position", position);
            addIfNotBlank(extraInfo, "interaction", interaction);
            if (!extraInfo.isEmpty()) {
                request.put("extraInfo", extraInfo);
            }

            return handleResult(projectClient.getOrCreateEntityRelation(request), "添加道具", data -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("relationId", data.get("id"));
                response.put("message", "道具已添加到分镜");
                return response;
            });
        });
    }

    @Tool(name = "add_dialogue_to_storyboard", description = "为分镜添加对白（便捷方法，非幂等）。创建分镜与角色的 speaks_in 关系，包含对白内容、情绪、语气。同一角色可有多条对白（不同 sequence）。")
    @AgentToolSpec(
            displayName = "添加对白到分镜",
            summary = "为分镜添加角色对白（speaks_in）。",
            purpose = "用于为分镜指定角色的台词及其情绪和语气。",
            actionType = ToolActionType.WRITE,
            tags = {"relation", "storyboard", "dialogue", "convenience"},
            usageNotes = {"非幂等操作：同一角色可添加多条对白（用 sequence 区分顺序）", "需配合 sequence 参数控制对白先后顺序"},
            errorCases = {"storyboardId、characterId 或 text 为空时会返回校验错误"},
            exampleInput = "{\"storyboardId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"characterId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"text\":\"你好世界\",\"emotion\":\"happy\",\"voiceStyle\":\"normal\",\"sequence\":1}",
            exampleOutput = "{\"success\":true,\"relationId\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"message\":\"对白已添加到分镜\"}"
    )
    public Map<String, Object> addDialogueToStoryboard(
            @ToolParam(description = "分镜ID（必填）") String storyboardId,
            @ToolParam(description = "说话角色ID（必填）") String characterId,
            @ToolParam(description = "对白内容（必填）") String text,
            @ToolParam(description = "对白情绪: neutral, happy, sad, angry, fearful, surprised, excited", required = false) String emotion,
            @ToolParam(description = "语气: whisper, soft, normal, loud, shouting", required = false) String voiceStyle,
            @ToolParam(description = "对白顺序", required = false) Integer sequence) {

        Map<String, Object> validationError = validateRequired(storyboardId, "分镜ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(characterId, "角色ID");
        if (validationError != null) return validationError;
        validationError = validateRequired(text, "对白内容");
        if (validationError != null) return validationError;

        return execute("添加对白到分镜", () -> {
            Map<String, Object> request = new HashMap<>();
            request.put("sourceType", "STORYBOARD");
            request.put("sourceId", storyboardId);
            request.put("targetType", "CHARACTER");
            request.put("targetId", characterId);
            request.put("relationType", "speaks_in");
            addIfNotNull(request, "sequence", sequence);

            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put("text", text);
            addIfNotBlank(extraInfo, "emotion", emotion);
            addIfNotBlank(extraInfo, "voiceStyle", voiceStyle);
            request.put("extraInfo", extraInfo);

            return handleResult(projectClient.createEntityRelation(request), "添加对白", data -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("relationId", data.get("id"));
                response.put("message", "对白已添加到分镜");
                return response;
            });
        });
    }
}
