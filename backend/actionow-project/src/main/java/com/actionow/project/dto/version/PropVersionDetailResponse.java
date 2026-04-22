package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 道具版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 道具ID
     */
    private String propId;

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

    // ========== 道具业务字段快照 ==========

    /**
     * 作用域: WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID
     */
    private String scriptId;

    /**
     * 道具名称
     */
    private String name;

    /**
     * 道具描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    private String fixedDesc;

    /**
     * 道具类型
     */
    private String propType;

    /**
     * 外观数据
     */
    private Map<String, Object> appearanceData;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
