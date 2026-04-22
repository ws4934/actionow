package com.actionow.project.constant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 实体默认结构化数据
 * 用于在创建实体时初始化 JSON 字段的默认结构
 *
 * @author Actionow
 */
public final class EntityDefaults {

    private EntityDefaults() {
    }

    /**
     * 合并用户提供的数据到默认结构
     * 确保返回的 Map 包含所有默认字段，同时覆盖用户提供的值
     *
     * @param defaults 默认结构
     * @param provided 用户提供的数据（可为 null）
     * @return 合并后的结构
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeWithDefaults(Map<String, Object> defaults, Map<String, Object> provided) {
        if (provided == null || provided.isEmpty()) {
            return new HashMap<>(defaults);
        }

        Map<String, Object> result = new HashMap<>(defaults);
        for (Map.Entry<String, Object> entry : provided.entrySet()) {
            String key = entry.getKey();
            Object providedValue = entry.getValue();

            if (providedValue == null) {
                // 用户显式传 null，保留默认值
                continue;
            }

            Object defaultValue = result.get(key);
            if (defaultValue instanceof Map && providedValue instanceof Map) {
                // 递归合并嵌套 Map
                result.put(key, mergeWithDefaults((Map<String, Object>) defaultValue, (Map<String, Object>) providedValue));
            } else {
                // 直接覆盖
                result.put(key, providedValue);
            }
        }
        return result;
    }

    /**
     * 分镜 visualDesc 默认结构
     * 仅包含镜头属性，不包含实体引用（实体关系通过 entity_relation 表管理）
     */
    public static Map<String, Object> storyboardVisualDesc() {
        Map<String, Object> visualDesc = new HashMap<>();
        visualDesc.put("shotType", null);           // 景别: EXTREME_CLOSE_UP, CLOSE_UP, MEDIUM_CLOSE_UP, MEDIUM, MEDIUM_LONG, LONG, EXTREME_LONG
        visualDesc.put("cameraAngle", null);        // 机位角度: HIGH, LOW, EYE_LEVEL, DUTCH, BIRD_EYE, WORM_EYE
        visualDesc.put("cameraMovement", null);     // 镜头运动: STATIC, PAN, TILT, ZOOM, DOLLY, TRACKING, CRANE
        visualDesc.put("lighting", null);           // 光线: natural, dramatic, dim, bright
        visualDesc.put("colorGrading", null);       // 颜色分级: desaturated, high-contrast, warm-toned 等
        visualDesc.put("visualEffects", null);      // 视觉特效
        visualDesc.put("transition", new HashMap<String, Object>() {{
            put("type", null);                      // 转场类型: CUT, FADE, DISSOLVE, WIPE, IRIS
        }});
        // 注意：sceneId, sceneOverride, characters, props 已迁移到 entity_relation 表
        return visualDesc;
    }

    /**
     * 获取合并后的分镜 visualDesc
     */
    public static Map<String, Object> mergeStoryboardVisualDesc(Map<String, Object> provided) {
        return mergeWithDefaults(storyboardVisualDesc(), provided);
    }

    /**
     * 分镜 audioDesc 默认结构
     * 仅包含音频属性，不包含实体引用（对白关系通过 entity_relation 表管理）
     */
    public static Map<String, Object> storyboardAudioDesc() {
        Map<String, Object> audioDesc = new HashMap<>();
        audioDesc.put("narration", null);                // 旁白: {text, voiceType}
        audioDesc.put("soundEffects", new ArrayList<>()); // 音效: [{type, description, volume}]
        audioDesc.put("bgm", new HashMap<String, Object>() {{
            put("mood", null);    // 背景音乐情绪: happy, sad, tense, peaceful, epic, mysterious, romantic, melancholy
            put("genre", null);   // 音乐类型: orchestral, electronic, piano, guitar, ambient
            put("tempo", null);   // 节奏: slow, medium, fast
        }});
        // 注意：dialogues 已迁移到 entity_relation 表 (speaks_in 关系类型)
        return audioDesc;
    }

    /**
     * 获取合并后的分镜 audioDesc
     */
    public static Map<String, Object> mergeStoryboardAudioDesc(Map<String, Object> provided) {
        return mergeWithDefaults(storyboardAudioDesc(), provided);
    }

    /**
     * 角色 appearanceData 默认结构
     */
    public static Map<String, Object> characterAppearanceData() {
        Map<String, Object> appearanceData = new HashMap<>();
        appearanceData.put("bodyType", null);         // 体型: slim, average, athletic, muscular, chubby
        appearanceData.put("height", null);           // 身高: short, average, tall
        appearanceData.put("skinTone", null);         // 肤色: fair, light, medium, tan, dark
        appearanceData.put("faceShape", null);        // 脸型: oval, round, square, heart, long
        appearanceData.put("eyeColor", null);         // 眼睛颜色
        appearanceData.put("eyeShape", null);         // 眼型: almond, round, monolid, hooded
        appearanceData.put("hairColor", null);        // 发色
        appearanceData.put("hairStyle", null);        // 发型
        appearanceData.put("hairLength", null);       // 发长: short, medium, long
        appearanceData.put("distinguishingFeatures", new ArrayList<>()); // 显著特征
        appearanceData.put("defaultOutfit", new HashMap<String, Object>() {{
            put("style", null);   // 服装风格: casual, formal, sporty, traditional
        }});
        appearanceData.put("artStyle", null);         // 绘画风格: anime, realistic, cartoon, chibi
        return appearanceData;
    }

    /**
     * 获取合并后的角色 appearanceData
     */
    public static Map<String, Object> mergeCharacterAppearanceData(Map<String, Object> provided) {
        return mergeWithDefaults(characterAppearanceData(), provided);
    }

    /**
     * 场景 appearanceData 默认结构
     */
    public static Map<String, Object> sceneAppearanceData() {
        Map<String, Object> appearanceData = new HashMap<>();
        appearanceData.put("timeOfDay", null);        // 时间: DAWN, DAY, DUSK, NIGHT
        appearanceData.put("weather", null);          // 天气: sunny, cloudy, rainy, snowy, foggy, stormy
        appearanceData.put("season", null);           // 季节: spring, summer, autumn, winter
        appearanceData.put("location", null);         // 地点类型描述
        appearanceData.put("lighting", null);         // 光线: natural, artificial, dim, bright, dramatic
        appearanceData.put("colorTone", null);        // 色调: warm, cool, neutral, vibrant, muted
        appearanceData.put("mood", null);             // 氛围: peaceful, tense, romantic, mysterious, chaotic
        appearanceData.put("perspective", null);      // 透视: eye-level, bird-eye, worm-eye
        appearanceData.put("depth", null);            // 纵深: shallow, medium, deep
        appearanceData.put("keyElements", new ArrayList<>()); // 关键元素
        appearanceData.put("artStyle", null);         // 绘画风格
        return appearanceData;
    }

    /**
     * 获取合并后的场景 appearanceData
     */
    public static Map<String, Object> mergeSceneAppearanceData(Map<String, Object> provided) {
        return mergeWithDefaults(sceneAppearanceData(), provided);
    }

    /**
     * 道具 appearanceData 默认结构
     */
    public static Map<String, Object> propAppearanceData() {
        Map<String, Object> appearanceData = new HashMap<>();
        appearanceData.put("material", null);         // 材质: metal, wood, glass, plastic, fabric, stone
        appearanceData.put("texture", null);          // 质感: smooth, rough, glossy, matte
        appearanceData.put("color", null);            // 主色
        appearanceData.put("secondaryColor", null);   // 次要色
        appearanceData.put("size", null);             // 尺寸: tiny, small, handheld, medium, large, huge
        appearanceData.put("shape", null);            // 形状描述
        appearanceData.put("condition", null);        // 状态: new, used, worn, damaged, antique
        appearanceData.put("distinguishingFeatures", new ArrayList<>()); // 特殊特征
        appearanceData.put("functional", null);       // 是否可交互
        appearanceData.put("specialEffects", null);   // 特效描述
        appearanceData.put("artStyle", null);         // 绘画风格
        return appearanceData;
    }

    /**
     * 获取合并后的道具 appearanceData
     */
    public static Map<String, Object> mergePropAppearanceData(Map<String, Object> provided) {
        return mergeWithDefaults(propAppearanceData(), provided);
    }
}
