package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 角色数据 DTO
 * 对应 actionow-script 模块的 Character 实体
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterData {

    // ==================== 必填字段 ====================

    /**
     * 角色名称
     * 必填
     */
    private String name;

    // ==================== 推荐字段 ====================

    /**
     * 角色描述
     */
    private String description;

    // ==================== 可选字段 ====================

    /**
     * 固定描述词（用于 AI 生成时的固定 prompt）
     */
    private String fixedDesc;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 性别
     */
    private String gender;

    /**
     * 角色类型
     * PROTAGONIST(主角)、ANTAGONIST(反派)、SUPPORTING(配角)、BACKGROUND(背景人物)
     */
    private String characterType;

    /**
     * 外貌数据
     * 包含 hair, eyes, build, clothing 等
     */
    private Map<String, Object> appearance;

    /**
     * 语音种子 ID（用于 TTS）
     */
    private String voiceSeedId;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    // ==================== 角色类型常量 ====================

    public static final String TYPE_PROTAGONIST = "PROTAGONIST";
    public static final String TYPE_ANTAGONIST = "ANTAGONIST";
    public static final String TYPE_SUPPORTING = "SUPPORTING";
    public static final String TYPE_BACKGROUND = "BACKGROUND";
}
