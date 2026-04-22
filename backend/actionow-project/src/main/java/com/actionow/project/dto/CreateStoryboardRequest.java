package com.actionow.project.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 创建分镜请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryboardRequest {

    /**
     * 所属剧集ID
     */
    private String episodeId;

    /**
     * 分镜标题
     */
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    /**
     * 分镜描述/台词
     */
    @Size(max = 5000, message = "描述不能超过5000个字符")
    private String synopsis;

    /**
     * 时长（秒）
     */
    private Integer duration;

    /**
     * 视觉描述（仅镜头属性，不包含实体引用）
     * {
     *   "shotType": "MEDIUM",
     *   "cameraAngle": "EYE_LEVEL",
     *   "cameraMovement": "STATIC",
     *   "lighting": "dramatic",
     *   "colorGrading": "desaturated",
     *   "visualEffects": [],
     *   "transition": {"type": "CUT"}
     * }
     */
    private Map<String, Object> visualDesc;

    /**
     * 音频描述（仅非实体属性）
     * {
     *   "narration": {...},
     *   "soundEffects": [],
     *   "bgm": {"mood": "melancholy", "genre": "piano", "tempo": "slow"}
     * }
     */
    private Map<String, Object> audioDesc;

    /**
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 状态: DRAFT, GENERATING, COMPLETED, FAILED
     */
    private String status;

    /**
     * 指定序号
     */
    private Integer sequence;

    /**
     * 保存模式
     * OVERWRITE - 覆盖当前版本（不创建版本快照）
     * NEW_VERSION - 存为新版本（默认，推荐）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;

    // ==================== 实体关系字段 ====================

    /**
     * 场景ID
     */
    private String sceneId;

    /**
     * 场景属性覆盖
     */
    private Map<String, Object> sceneOverride;

    /**
     * 角色出现列表
     */
    private List<CharacterAppearance> characters;

    /**
     * 道具使用列表
     */
    private List<PropUsage> props;

    /**
     * 对白列表
     */
    private List<DialogueLine> dialogues;

    /**
     * 风格ID
     */
    private String styleId;

    /**
     * 角色出现信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterAppearance {
        /**
         * 角色ID
         */
        private String characterId;

        /**
         * 画面位置 (center, left, right, foreground, background)
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
         * 排序序号
         */
        private Integer sequence;
    }

    /**
     * 道具使用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PropUsage {
        /**
         * 道具ID
         */
        private String propId;

        /**
         * 画面位置
         */
        private String position;

        /**
         * 交互方式
         */
        private String interaction;

        /**
         * 道具状态
         */
        private String state;

        /**
         * 排序序号
         */
        private Integer sequence;
    }

    /**
     * 对白信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DialogueLine {
        /**
         * 角色ID
         */
        private String characterId;

        /**
         * 台词内容
         */
        private String text;

        /**
         * 情绪
         */
        private String emotion;

        /**
         * 语气
         */
        private String voiceStyle;

        /**
         * 时间戳
         */
        private Map<String, Object> timing;

        /**
         * 排序序号（对白顺序）
         */
        private Integer sequence;
    }
}
