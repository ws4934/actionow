package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 剧集数据 DTO
 * 对应 actionow-script 模块的 Episode 实体
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeData {

    // ==================== 必填字段 ====================

    /**
     * 剧集标题
     * 必填
     */
    private String title;

    // ==================== 推荐字段 ====================

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 剧集简介
     */
    private String synopsis;

    // ==================== 可选字段 ====================

    /**
     * 剧集内容
     */
    private String content;

    /**
     * 分镜列表
     */
    private List<StoryboardData> storyboards;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
