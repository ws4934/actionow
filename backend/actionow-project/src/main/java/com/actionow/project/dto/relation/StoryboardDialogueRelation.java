package com.actionow.project.dto.relation;

import com.actionow.project.entity.EntityRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 分镜-对白关系
 * 存储角色在分镜中的对白信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardDialogueRelation {

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
     * 排序序号（对白顺序）
     */
    private Integer sequence;

    /**
     * 对白索引（在对白列表中的位置）
     */
    private Integer dialogueIndex;

    /**
     * 台词内容
     */
    private String text;

    /**
     * 情绪 (excited, sad, angry, calm 等)
     */
    private String emotion;

    /**
     * 语气 (loud, whisper, normal 等)
     */
    private String voiceStyle;

    /**
     * 时间戳
     * {
     *   "start": 0,
     *   "end": 2000
     * }
     */
    private Map<String, Object> timing;

    /**
     * 从 EntityRelation 转换
     */
    public static StoryboardDialogueRelation fromEntity(EntityRelation relation) {
        if (relation == null) {
            return null;
        }
        StoryboardDialogueRelation result = new StoryboardDialogueRelation();
        result.setRelationId(relation.getId());
        result.setCharacterId(relation.getTargetId());
        result.setSequence(relation.getSequence());

        if (relation.getExtraInfo() != null) {
            Map<String, Object> extra = relation.getExtraInfo();
            if (extra.get("dialogueIndex") instanceof Number) {
                result.setDialogueIndex(((Number) extra.get("dialogueIndex")).intValue());
            }
            result.setText((String) extra.get("text"));
            result.setEmotion((String) extra.get("emotion"));
            result.setVoiceStyle((String) extra.get("voiceStyle"));
            Object timing = extra.get("timing");
            if (timing instanceof Map) {
                result.setTiming((Map<String, Object>) timing);
            }
        }
        return result;
    }
}
