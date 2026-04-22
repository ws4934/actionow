package com.actionow.agent.scriptwriting.tools;

import com.actionow.agent.feign.ProjectFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体查询工具类（SAA v2）
 * 提供批量查询、分镜关联实体展开等高级查询功能
 *
 * @author Actionow
 */
@Slf4j
@Component
public class EntityQueryTools extends AbstractProjectTool {

    public EntityQueryTools(ProjectFeignClient projectClient) {
        super(projectClient);
    }

    @Tool(name = "batch_get_entities", description = "批量获取多类型实体详情。适用于获取分镜中引用的所有角色、场景、道具，一次调用即可获取全部关联实体信息，避免多次单独查询。")
    public Map<String, Object> batchGetEntities(
            @ToolParam(description = "角色ID列表，逗号分隔。例如: id1,id2,id3", required = false) String characterIds,
            @ToolParam(description = "场景ID列表，逗号分隔。例如: id1,id2", required = false) String sceneIds,
            @ToolParam(description = "道具ID列表，逗号分隔。例如: id1,id2,id3", required = false) String propIds,
            @ToolParam(description = "风格ID列表，逗号分隔。例如: id1", required = false) String styleIds) {

        return execute("批量查询实体", () -> {
            Map<String, List<String>> request = new HashMap<>();

            if (characterIds != null && !characterIds.isBlank()) {
                request.put("characterIds", Arrays.asList(characterIds.split(",")));
            }
            if (sceneIds != null && !sceneIds.isBlank()) {
                request.put("sceneIds", Arrays.asList(sceneIds.split(",")));
            }
            if (propIds != null && !propIds.isBlank()) {
                request.put("propIds", Arrays.asList(propIds.split(",")));
            }
            if (styleIds != null && !styleIds.isBlank()) {
                request.put("styleIds", Arrays.asList(styleIds.split(",")));
            }

            if (request.isEmpty()) {
                return error("至少需要提供一种实体ID列表");
            }

            var result = projectClient.batchQueryEntities(request);
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);

                Map<String, List<Map<String, Object>>> data = result.getData();
                int totalCount = 0;

                if (data.containsKey("characters")) {
                    response.put("characters", data.get("characters"));
                    totalCount += data.get("characters").size();
                }
                if (data.containsKey("scenes")) {
                    response.put("scenes", data.get("scenes"));
                    totalCount += data.get("scenes").size();
                }
                if (data.containsKey("props")) {
                    response.put("props", data.get("props"));
                    totalCount += data.get("props").size();
                }
                if (data.containsKey("styles")) {
                    response.put("styles", data.get("styles"));
                    totalCount += data.get("styles").size();
                }

                response.put("totalCount", totalCount);
                return response;
            }
            return error("批量查询实体失败: " + result.getMessage());
        });
    }

    @Tool(name = "get_storyboard_with_entities", description = "获取分镜详情并展开关联实体。一次调用返回分镜信息及其引用的所有角色、场景、道具的完整详情，无需多次查询。关联实体信息（角色、场景、道具、对白）直接包含在分镜响应的 characters、scene、props、dialogues 字段中。")
    public Map<String, Object> getStoryboardWithEntities(
            @ToolParam(description = "分镜ID（必填）") String storyboardId) {

        Map<String, Object> validationError = validateRequired(storyboardId, "分镜ID");
        if (validationError != null) return validationError;

        return execute("获取分镜及关联实体", () -> {
            // 获取分镜详情（已包含关联实体信息）
            var storyboardResult = projectClient.getStoryboard(storyboardId);
            if (!storyboardResult.isSuccess()) {
                return error("获取分镜失败: " + storyboardResult.getMessage());
            }

            Map<String, Object> storyboard = storyboardResult.getData();

            // 从分镜响应中提取关联实体信息（现在直接在响应中，而非嵌入 visualDesc/audioDesc）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> characters = (List<Map<String, Object>>) storyboard.get("characters");
            @SuppressWarnings("unchecked")
            Map<String, Object> scene = (Map<String, Object>) storyboard.get("scene");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> props = (List<Map<String, Object>>) storyboard.get("props");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dialogues = (List<Map<String, Object>>) storyboard.get("dialogues");

            // 提取关联实体ID用于批量查询完整详情
            Set<String> characterIds = new HashSet<>();
            Set<String> propIds = new HashSet<>();
            String sceneId = null;

            // 从 characters 关系中提取角色ID
            if (characters != null) {
                for (Map<String, Object> c : characters) {
                    String id = (String) c.get("characterId");
                    if (id != null && !id.isBlank()) {
                        characterIds.add(id);
                    }
                }
            }

            // 从 scene 关系中提取场景ID
            if (scene != null) {
                sceneId = (String) scene.get("sceneId");
            }

            // 从 props 关系中提取道具ID
            if (props != null) {
                for (Map<String, Object> p : props) {
                    String id = (String) p.get("propId");
                    if (id != null && !id.isBlank()) {
                        propIds.add(id);
                    }
                }
            }

            // 从 dialogues 关系中提取说话角色ID
            if (dialogues != null) {
                for (Map<String, Object> d : dialogues) {
                    String id = (String) d.get("characterId");
                    if (id != null && !id.isBlank()) {
                        characterIds.add(id);
                    }
                }
            }

            // 批量查询关联实体详情
            Map<String, List<String>> batchRequest = new HashMap<>();
            if (!characterIds.isEmpty()) {
                batchRequest.put("characterIds", new ArrayList<>(characterIds));
            }
            if (sceneId != null && !sceneId.isBlank()) {
                batchRequest.put("sceneIds", List.of(sceneId));
            }
            if (!propIds.isEmpty()) {
                batchRequest.put("propIds", new ArrayList<>(propIds));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("storyboard", storyboard);

            // 添加关系信息（带布局/交互/情绪等元数据）
            if (characters != null && !characters.isEmpty()) {
                response.put("characterRelations", characters);
            }
            if (props != null && !props.isEmpty()) {
                response.put("propRelations", props);
            }
            if (dialogues != null && !dialogues.isEmpty()) {
                response.put("dialogueRelations", dialogues);
            }
            if (scene != null) {
                response.put("sceneRelation", scene);
            }

            // 批量获取实体完整详情
            if (!batchRequest.isEmpty()) {
                var entitiesResult = projectClient.batchQueryEntities(batchRequest);
                if (entitiesResult.isSuccess()) {
                    Map<String, List<Map<String, Object>>> entities = entitiesResult.getData();
                    if (entities.containsKey("characters")) {
                        response.put("characterDetails", entities.get("characters"));
                    }
                    if (entities.containsKey("scenes")) {
                        response.put("sceneDetail", entities.get("scenes").isEmpty() ? null : entities.get("scenes").get(0));
                    }
                    if (entities.containsKey("props")) {
                        response.put("propDetails", entities.get("props"));
                    }
                }
            }

            // 统计信息
            Map<String, Object> summary = new HashMap<>();
            summary.put("characterCount", characterIds.size());
            summary.put("propCount", propIds.size());
            summary.put("dialogueCount", dialogues != null ? dialogues.size() : 0);
            summary.put("hasScene", sceneId != null && !sceneId.isBlank());
            response.put("summary", summary);

            return response;
        });
    }

    @Tool(name = "get_storyboard_relations", description = "获取分镜的实体关系。返回分镜与角色（appears_in）、场景（takes_place_in）、道具（uses）、对白（speaks_in）的关系数据，包含位置、动作、表情、交互等元信息。")
    public Map<String, Object> getStoryboardRelations(
            @ToolParam(description = "分镜ID（必填）") String storyboardId) {

        Map<String, Object> validationError = validateRequired(storyboardId, "分镜ID");
        if (validationError != null) return validationError;

        return execute("获取分镜关系", () -> {
            var result = projectClient.getStoryboardRelations(storyboardId);
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("storyboardId", storyboardId);
                response.put("relations", result.getData());
                return response;
            }
            return error("获取分镜关系失败: " + result.getMessage());
        });
    }
}
