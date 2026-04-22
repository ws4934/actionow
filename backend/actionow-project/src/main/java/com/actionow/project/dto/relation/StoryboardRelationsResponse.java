package com.actionow.project.dto.relation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分镜关系聚合响应
 * 包含分镜的所有关联实体关系
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardRelationsResponse {

    /**
     * 分镜ID
     */
    private String storyboardId;

    /**
     * 场景关系（分镜发生的场景）
     */
    private StoryboardSceneRelation scene;

    /**
     * 角色出现关系列表
     */
    private List<StoryboardCharacterRelation> characters;

    /**
     * 道具使用关系列表
     */
    private List<StoryboardPropRelation> props;

    /**
     * 对白关系列表
     */
    private List<StoryboardDialogueRelation> dialogues;

    /**
     * 风格关系（分镜使用的风格）
     */
    private String styleId;

    /**
     * 风格名称
     */
    private String styleName;
}
