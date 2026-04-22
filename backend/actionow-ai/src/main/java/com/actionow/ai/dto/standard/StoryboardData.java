package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 分镜数据 DTO
 * 对应 actionow-script 模块的 Storyboard 实体
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardData {

    // ==================== 可选字段 ====================

    /**
     * 分镜标题
     */
    private String title;

    /**
     * 排序序号
     * 推荐
     */
    private Integer sequence;

    /**
     * 分镜描述/台词
     * 推荐
     */
    private String synopsis;

    /**
     * 时长（秒）
     */
    private Integer duration;

    // ==================== 视觉描述 ====================

    /**
     * 镜头类型
     * CLOSE_UP(特写)、MEDIUM(中景)、LONG(远景)、EXTREME_LONG(大远景)、OVER_SHOULDER(过肩)
     */
    private String shotType;

    /**
     * 镜头运动
     * PAN(摇镜)、TILT(俯仰)、ZOOM(变焦)、DOLLY(推拉)、TRACK(跟踪)、STATIC(固定)
     */
    private String cameraMovement;

    /**
     * 动作描述
     */
    private String actionDesc;

    /**
     * 出场角色 ID 列表
     */
    private List<String> characterIds;

    /**
     * 场景 ID
     */
    private String sceneId;

    /**
     * 视觉描述（完整结构化数据）
     */
    private Map<String, Object> visualDesc;

    // ==================== 音频描述 ====================

    /**
     * 对白
     */
    private String dialogue;

    /**
     * 旁白
     */
    private String voiceOver;

    /**
     * 音效描述
     */
    private String soundEffect;

    /**
     * 背景音乐描述
     */
    private String bgm;

    /**
     * 音频描述（完整结构化数据）
     */
    private Map<String, Object> audioDesc;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    // ==================== 镜头类型常量 ====================

    public static final String SHOT_CLOSE_UP = "CLOSE_UP";
    public static final String SHOT_MEDIUM = "MEDIUM";
    public static final String SHOT_LONG = "LONG";
    public static final String SHOT_EXTREME_LONG = "EXTREME_LONG";
    public static final String SHOT_OVER_SHOULDER = "OVER_SHOULDER";

    // ==================== 镜头运动常量 ====================

    public static final String CAMERA_PAN = "PAN";
    public static final String CAMERA_TILT = "TILT";
    public static final String CAMERA_ZOOM = "ZOOM";
    public static final String CAMERA_DOLLY = "DOLLY";
    public static final String CAMERA_TRACK = "TRACK";
    public static final String CAMERA_STATIC = "STATIC";
}
