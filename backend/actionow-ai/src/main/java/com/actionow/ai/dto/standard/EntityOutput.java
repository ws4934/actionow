package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实体输出 DTO
 * 包含实体类型和实体数据
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityOutput {

    /**
     * 实体类型
     * 必填
     */
    private EntityType entityType;

    /**
     * 实体数据
     * 必填，类型由 entityType 决定
     */
    private Object data;

    // ==================== 工厂方法 ====================

    public static EntityOutput character(CharacterData data) {
        return new EntityOutput(EntityType.CHARACTER, data);
    }

    public static EntityOutput scene(SceneData data) {
        return new EntityOutput(EntityType.SCENE, data);
    }

    public static EntityOutput prop(PropData data) {
        return new EntityOutput(EntityType.PROP, data);
    }

    public static EntityOutput style(StyleData data) {
        return new EntityOutput(EntityType.STYLE, data);
    }

    public static EntityOutput storyboard(StoryboardData data) {
        return new EntityOutput(EntityType.STORYBOARD, data);
    }

    public static EntityOutput script(ScriptData data) {
        return new EntityOutput(EntityType.SCRIPT, data);
    }

    public static EntityOutput episode(EpisodeData data) {
        return new EntityOutput(EntityType.EPISODE, data);
    }

    // ==================== 类型转换方法 ====================

    @SuppressWarnings("unchecked")
    public <T> T getDataAs(Class<T> clazz) {
        if (data == null) {
            return null;
        }
        if (clazz.isInstance(data)) {
            return (T) data;
        }
        return null;
    }

    public CharacterData asCharacter() {
        return getDataAs(CharacterData.class);
    }

    public SceneData asScene() {
        return getDataAs(SceneData.class);
    }

    public PropData asProp() {
        return getDataAs(PropData.class);
    }

    public StyleData asStyle() {
        return getDataAs(StyleData.class);
    }

    public StoryboardData asStoryboard() {
        return getDataAs(StoryboardData.class);
    }

    public ScriptData asScript() {
        return getDataAs(ScriptData.class);
    }

    public EpisodeData asEpisode() {
        return getDataAs(EpisodeData.class);
    }
}
