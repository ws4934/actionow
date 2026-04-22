package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 剧本数据 DTO
 * 对应 actionow-script 模块的 Script 实体
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptData {

    // ==================== 必填字段 ====================

    /**
     * 剧本标题
     * 必填
     */
    private String title;

    // ==================== 推荐字段 ====================

    /**
     * 剧本简介/梗概
     */
    private String synopsis;

    // ==================== 可选字段 ====================

    /**
     * 剧本正文内容
     */
    private String content;

    /**
     * 剧集列表
     */
    private List<EpisodeData> episodes;

    /**
     * 角色列表（解析出的角色）
     */
    private List<CharacterData> characters;

    /**
     * 场景列表（解析出的场景）
     */
    private List<SceneData> scenes;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    // ==================== 剧本状态常量 ====================

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
}
