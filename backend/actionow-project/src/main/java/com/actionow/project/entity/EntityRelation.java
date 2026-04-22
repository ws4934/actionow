package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 实体关系
 * 记录实体之间的关联关系，如分镜与角色、场景、道具的关系
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_entity_relation", autoResultMap = true)
public class EntityRelation extends TenantBaseEntity {

    /**
     * 源实体类型: STORYBOARD, EPISODE, CHARACTER 等
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 源实体ID
     */
    @TableField("source_id")
    private String sourceId;

    /**
     * 源实体版本ID（可选，用于版本关联）
     */
    @TableField("source_version_id")
    private String sourceVersionId;

    /**
     * 目标实体类型: CHARACTER, SCENE, PROP, STYLE 等
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 目标实体ID
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 目标实体版本ID（可选）
     */
    @TableField("target_version_id")
    private String targetVersionId;

    /**
     * 关系类型:
     * - appears_in: 角色出现在分镜中（视觉）
     * - speaks_in: 角色在分镜中说话（对白）
     * - takes_place_in: 分镜发生在场景中
     * - uses: 分镜使用道具
     * - styled_by: 分镜使用风格
     * - episode_has_character: 剧集包含角色
     * - episode_has_scene: 剧集包含场景
     * - episode_has_prop: 剧集包含道具
     * - equipped_with: 角色装备道具
     * - owns: 角色拥有道具
     * - character_relationship: 角色间关系
     */
    @TableField("relation_type")
    private String relationType;

    /**
     * 关系标签（可选的显示名称）
     */
    @TableField("relation_label")
    private String relationLabel;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序序号（用于同类关系的排序，如多个角色的出场顺序）
     */
    private Integer sequence;

    /**
     * 扩展信息（存储关系的元数据）
     *
     * appears_in 关系:
     * {
     *   "position": "center",           // 画面位置
     *   "positionDetail": "站在窗前",    // 位置描述
     *   "action": "looking out",        // 动作
     *   "expression": "thoughtful",     // 表情
     *   "outfitOverride": {...}         // 服装覆盖
     * }
     *
     * speaks_in 关系:
     * {
     *   "dialogueIndex": 0,             // 对白顺序
     *   "text": "你好世界",              // 台词内容
     *   "emotion": "excited",           // 情绪
     *   "voiceStyle": "loud",           // 语气
     *   "timing": {"start": 0, "end": 2000}  // 时间戳
     * }
     *
     * takes_place_in 关系:
     * {
     *   "sceneOverride": {              // 场景属性覆盖
     *     "timeOfDay": "NIGHT",
     *     "weather": "rainy"
     *   }
     * }
     *
     * uses 关系:
     * {
     *   "position": "foreground",
     *   "interaction": "held by character",
     *   "state": "glowing"
     * }
     *
     * character_relationship 关系:
     * {
     *   "relationshipType": "sibling",  // 关系类型
     *   "bidirectional": true           // 是否双向
     * }
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;
}
