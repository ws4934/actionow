/**
 * 剧本解析模板（实体输出类型）- 标准响应格式 v2.0
 *
 * 适用场景：解析剧本内容，提取角色、场景、道具等实体
 *
 * 可用变量:
 *   - inputs: 用户输入参数 {scriptId, content, parseOptions}
 *   - config: 提供商配置 {llmEndpoint, llmModel, ...}
 *   - response: LLM 返回的解析结果
 *   - db: 数据库操作绑定
 *   - notify: 通知绑定
 *   - json: JSON工具
 *   - log: 日志工具
 *
 * 预期输入:
 *   - inputs.scriptId: 剧本ID
 *   - inputs.content: 剧本文本内容
 *   - inputs.parseOptions: 解析选项 {extractCharacters, extractScenes, extractProps}
 *
 * 返回格式 (StandardResponse):
 * {
 *   "outputType": "ENTITY_MIXED",
 *   "status": "SUCCEEDED" | "FAILED",
 *   "entities": [
 *     { "entityType": "CHARACTER", "data": {...} },
 *     { "entityType": "SCENE", "data": {...} },
 *     ...
 *   ],
 *   "error": { code, message, retryable },
 *   "metadata": { ... }
 * }
 */

// ========== 验证输入 ==========

def scriptId = inputs.scriptId
def content = inputs.content
def options = inputs.parseOptions ?: [extractCharacters: true, extractScenes: true, extractProps: true]

if (!scriptId || !content) {
    return [
        outputType: "ENTITY_MIXED",
        status: "FAILED",
        error: [
            code: "INVALID_REQUEST",
            message: "缺少必要参数: scriptId 或 content",
            retryable: false
        ]
    ]
}

log.info("Starting script parsing for: {}", scriptId)

// ========== 检查 LLM 响应 ==========

if (!response) {
    return [
        outputType: "ENTITY_MIXED",
        status: "FAILED",
        error: [
            code: "EMPTY_RESPONSE",
            message: "LLM 返回空响应",
            retryable: true
        ]
    ]
}

// 解析 LLM 响应
def parsed = response
if (parsed instanceof String) {
    try {
        parsed = json.parse(parsed)
    } catch (Exception e) {
        return [
            outputType: "ENTITY_MIXED",
            status: "FAILED",
            error: [
                code: "PARSE_ERROR",
                message: "无法解析 LLM 响应: " + e.message,
                retryable: false
            ]
        ]
    }
}

// ========== 构建实体列表 ==========

def entities = []
def summary = [
    characterCount: 0,
    sceneCount: 0,
    propCount: 0,
    styleCount: 0
]

// 提取角色
if (options.extractCharacters && parsed.characters) {
    log.info("Extracting {} characters", parsed.characters.size())

    parsed.characters.each { char ->
        entities << [
            entityType: "CHARACTER",
            data: [
                name: char.name,
                description: char.description ?: "",
                characterType: mapCharacterType(char.type ?: char.role),
                age: char.age,
                gender: char.gender,
                traits: char.traits,
                appearance: char.appearance,
                background: char.background,
                voiceStyle: char.voiceStyle ?: char.voice_style,
                referenceImageUrl: char.referenceImageUrl ?: char.reference_image_url,
                scope: "SCRIPT",
                scriptId: scriptId
            ]
        ]
        summary.characterCount++
    }
}

// 提取场景
if (options.extractScenes && parsed.scenes) {
    log.info("Extracting {} scenes", parsed.scenes.size())

    parsed.scenes.each { scene ->
        entities << [
            entityType: "SCENE",
            data: [
                name: scene.name,
                description: scene.description ?: "",
                sceneType: scene.sceneType ?: scene.scene_type ?: scene.locationType ?: "INTERIOR",
                appearanceData: [
                    mood: scene.mood ?: scene.atmosphere,
                    timeOfDay: scene.timeOfDay ?: scene.time_of_day,
                    weather: scene.weather,
                    location: scene.location,
                    lightingCondition: scene.lightingCondition ?: scene.lighting,
                    colorPalette: scene.colorPalette ?: scene.color_palette
                ],
                referenceImageUrl: scene.referenceImageUrl ?: scene.reference_image_url,
                scope: "SCRIPT",
                scriptId: scriptId
            ]
        ]
        summary.sceneCount++
    }
}

// 提取道具
if (options.extractProps && parsed.props) {
    log.info("Extracting {} props", parsed.props.size())

    parsed.props.each { prop ->
        entities << [
            entityType: "PROP",
            data: [
                name: prop.name,
                description: prop.description ?: "",
                importance: prop.importance ?: "NORMAL",
                category: prop.category,
                material: prop.material,
                color: prop.color,
                dimensions: prop.dimensions,
                referenceImageUrl: prop.referenceImageUrl ?: prop.reference_image_url,
                scope: "SCRIPT",
                scriptId: scriptId
            ]
        ]
        summary.propCount++
    }
}

// 提取风格 (如果有)
if (options.extractStyles && parsed.styles) {
    log.info("Extracting {} styles", parsed.styles.size())

    parsed.styles.each { style ->
        entities << [
            entityType: "STYLE",
            data: [
                name: style.name,
                description: style.description ?: "",
                styleType: style.styleType ?: style.type,
                visualPrompt: style.visualPrompt ?: style.visual_prompt,
                negativePrompt: style.negativePrompt ?: style.negative_prompt,
                parameters: style.parameters ?: [:],
                referenceImageUrls: style.referenceImageUrls ?: style.reference_images,
                scope: "SCRIPT",
                scriptId: scriptId
            ]
        ]
        summary.styleCount++
    }
}

// 提取分镜 (如果有)
if (options.extractStoryboards && parsed.storyboards) {
    log.info("Extracting {} storyboards", parsed.storyboards.size())

    parsed.storyboards.each { sb ->
        entities << [
            entityType: "STORYBOARD",
            data: [
                sequence: sb.sequence,
                synopsis: sb.synopsis ?: sb.description,
                dialogue: sb.dialogue,
                action: sb.action,
                cameraAngle: sb.cameraAngle ?: sb.camera_angle,
                shotType: sb.shotType ?: sb.shot_type,
                duration: sb.duration,
                sceneId: sb.sceneId ?: sb.scene_id,
                characterIds: sb.characterIds ?: sb.character_ids ?: [],
                propIds: sb.propIds ?: sb.prop_ids ?: []
            ]
        ]
    }
}

// ========== 确定输出类型 ==========

def outputType = "ENTITY_MIXED"
if (entities.size() == 1) {
    outputType = "ENTITY_" + entities[0].entityType
} else if (entities.every { it.entityType == "CHARACTER" }) {
    outputType = "ENTITY_CHARACTER"
} else if (entities.every { it.entityType == "SCENE" }) {
    outputType = "ENTITY_SCENE"
} else if (entities.every { it.entityType == "PROP" }) {
    outputType = "ENTITY_PROP"
} else if (entities.every { it.entityType == "STYLE" }) {
    outputType = "ENTITY_STYLE"
} else if (entities.every { it.entityType == "STORYBOARD" }) {
    outputType = "ENTITY_STORYBOARD"
}

log.info("Script parsing completed. Characters: {}, Scenes: {}, Props: {}, Styles: {}",
    summary.characterCount, summary.sceneCount, summary.propCount, summary.styleCount)

// ========== 返回标准响应 ==========

return [
    outputType: outputType,
    status: "SUCCEEDED",
    entities: entities,
    metadata: [
        extra: [
            scriptId: scriptId,
            summary: summary,
            totalEntities: entities.size()
        ],
        raw: parsed
    ]
]

// ========== 辅助方法 ==========

def mapCharacterType(String type) {
    if (!type) return "SUPPORTING"

    switch (type.toLowerCase()) {
        case "主角":
        case "protagonist":
        case "main":
            return "PROTAGONIST"
        case "反派":
        case "antagonist":
        case "villain":
            return "ANTAGONIST"
        case "配角":
        case "supporting":
        case "secondary":
            return "SUPPORTING"
        case "群演":
        case "extra":
        case "background":
            return "EXTRA"
        default:
            return "SUPPORTING"
    }
}
