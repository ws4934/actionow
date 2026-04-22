package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 剧集版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 剧集ID
     */
    private String episodeId;

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

    // ========== 剧集业务字段快照 ==========

    /**
     * 所属剧本ID
     */
    private String scriptId;

    /**
     * 剧集标题
     */
    private String title;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED
     */
    private String status;

    /**
     * 剧集简介
     */
    private String synopsis;

    /**
     * 剧集内容
     */
    private String content;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 文档素材ID
     */
    private String docAssetId;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
