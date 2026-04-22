package com.actionow.project.dto.relation;

import com.actionow.project.entity.EntityRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 分镜-角色出现关系
 * 存储角色在分镜中的视觉出现信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardCharacterRelation {

    /**
     * 关系ID
     */
    private String relationId;

    /**
     * 角色ID
     */
    private String characterId;

    /**
     * 角色名称（从 Character 实体获取）
     */
    private String characterName;

    /**
     * 角色类型（从 Character 实体获取）
     */
    private String characterType;

    /**
     * 角色封面URL（从 Character 的 coverAssetId 获取）
     */
    private String coverUrl;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 画面位置 (center, left, right, foreground, background 等)
     */
    private String position;

    /**
     * 位置详细描述
     */
    private String positionDetail;

    /**
     * 动作描述
     */
    private String action;

    /**
     * 表情
     */
    private String expression;

    /**
     * 服装覆盖
     */
    private Map<String, Object> outfitOverride;

    /**
     * 从 EntityRelation 转换
     */
    public static StoryboardCharacterRelation fromEntity(EntityRelation relation) {
        if (relation == null) {
            return null;
        }
        StoryboardCharacterRelation result = new StoryboardCharacterRelation();
        result.setRelationId(relation.getId());
        result.setCharacterId(relation.getTargetId());
        result.setSequence(relation.getSequence());

        if (relation.getExtraInfo() != null) {
            Map<String, Object> extra = relation.getExtraInfo();
            result.setPosition((String) extra.get("position"));
            result.setPositionDetail((String) extra.get("positionDetail"));
            result.setAction((String) extra.get("action"));
            result.setExpression((String) extra.get("expression"));
            Object outfitOverride = extra.get("outfitOverride");
            if (outfitOverride instanceof Map) {
                result.setOutfitOverride((Map<String, Object>) outfitOverride);
            }
        }
        return result;
    }
}
