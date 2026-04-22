package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 风格版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 风格ID
     */
    private String styleId;

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

    // ========== 风格业务字段快照 ==========

    /**
     * 作用域: WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID
     */
    private String scriptId;

    /**
     * 风格名称
     */
    private String name;

    /**
     * 风格描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    private String fixedDesc;

    /**
     * 风格参数
     */
    private Map<String, Object> styleParams;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
