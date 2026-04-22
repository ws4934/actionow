package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 剧本版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 剧本ID
     */
    private String scriptId;

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

    // ========== 剧本业务字段快照 ==========

    /**
     * 剧本标题
     */
    private String title;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED, ARCHIVED
     */
    private String status;

    /**
     * 剧本简介/梗概
     */
    private String synopsis;

    /**
     * 剧本正文内容
     */
    private String content;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 关联的文档素材ID
     */
    private String docAssetId;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
