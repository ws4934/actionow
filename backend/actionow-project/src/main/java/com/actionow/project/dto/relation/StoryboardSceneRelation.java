package com.actionow.project.dto.relation;

import com.actionow.project.entity.EntityRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 分镜-场景关系
 * 存储分镜发生的场景及场景属性覆盖
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardSceneRelation {

    /**
     * 关系ID
     */
    private String relationId;

    /**
     * 场景ID
     */
    private String sceneId;

    /**
     * 场景名称（从 Scene 实体获取）
     */
    private String sceneName;

    /**
     * 场景描述（从 Scene 实体获取）
     */
    private String sceneDescription;

    /**
     * 场景封面URL（从 Scene 的 coverAssetId 获取）
     */
    private String coverUrl;

    /**
     * 场景属性覆盖
     * {
     *   "timeOfDay": "NIGHT",
     *   "weather": "rainy",
     *   "lighting": "dramatic"
     * }
     */
    private Map<String, Object> sceneOverride;

    /**
     * 从 EntityRelation 转换
     */
    public static StoryboardSceneRelation fromEntity(EntityRelation relation) {
        if (relation == null) {
            return null;
        }
        StoryboardSceneRelation result = new StoryboardSceneRelation();
        result.setRelationId(relation.getId());
        result.setSceneId(relation.getTargetId());
        if (relation.getExtraInfo() != null) {
            Object sceneOverride = relation.getExtraInfo().get("sceneOverride");
            if (sceneOverride instanceof Map) {
                result.setSceneOverride((Map<String, Object>) sceneOverride);
            }
        }
        return result;
    }
}
