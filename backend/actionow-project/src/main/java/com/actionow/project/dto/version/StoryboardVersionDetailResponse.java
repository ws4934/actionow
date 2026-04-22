package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 分镜版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 分镜ID
     */
    private String storyboardId;

    /**
     * 版本号
     */
    private Integer versionNumber;

    /**
     * 变更摘要
     */
    private String changeSummary;

    /**
     * 创建人ID
     */
    private String createdBy;

    /**
     * 创建人名称
     */
    private String createdByName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 是否为当前版本
     */
    private Boolean isCurrent;

    // ========== 分镜业务字段快照 ==========

    /**
     * 所属剧本ID
     */
    private String scriptId;

    /**
     * 所属剧集ID
     */
    private String episodeId;

    /**
     * 分镜标题
     */
    private String title;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 状态: DRAFT, GENERATING, COMPLETED, FAILED
     */
    private String status;

    /**
     * 分镜描述/台词
     */
    private String synopsis;

    /**
     * 时长（秒）
     */
    private Integer duration;

    /**
     * 视觉描述
     */
    private Map<String, Object> visualDesc;

    /**
     * 音频描述
     */
    private Map<String, Object> audioDesc;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
