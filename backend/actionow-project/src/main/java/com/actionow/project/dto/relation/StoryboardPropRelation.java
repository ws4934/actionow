package com.actionow.project.dto.relation;

import com.actionow.project.entity.EntityRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 分镜-道具使用关系
 * 存储道具在分镜中的使用信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardPropRelation {

    /**
     * 关系ID
     */
    private String relationId;

    /**
     * 道具ID
     */
    private String propId;

    /**
     * 道具名称（从 Prop 实体获取）
     */
    private String propName;

    /**
     * 道具类型（从 Prop 实体获取）
     */
    private String propType;

    /**
     * 道具封面URL（从 Prop 的 coverAssetId 获取）
     */
    private String coverUrl;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 画面位置 (foreground, background, center 等)
     */
    private String position;

    /**
     * 交互方式 (held by character, on table, floating 等)
     */
    private String interaction;

    /**
     * 道具状态 (glowing, broken, open 等)
     */
    private String state;

    /**
     * 从 EntityRelation 转换
     */
    public static StoryboardPropRelation fromEntity(EntityRelation relation) {
        if (relation == null) {
            return null;
        }
        StoryboardPropRelation result = new StoryboardPropRelation();
        result.setRelationId(relation.getId());
        result.setPropId(relation.getTargetId());
        result.setSequence(relation.getSequence());

        if (relation.getExtraInfo() != null) {
            Map<String, Object> extra = relation.getExtraInfo();
            result.setPosition((String) extra.get("position"));
            result.setInteraction((String) extra.get("interaction"));
            result.setState((String) extra.get("state"));
        }
        return result;
    }
}
