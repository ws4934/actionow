package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 场景版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 场景ID
     */
    private String sceneId;

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

    // ========== 场景业务字段快照 ==========

    /**
     * 作用域: SYSTEM-系统预置, WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 场景类型: INTERIOR-室内, EXTERIOR-室外, MIXED-混合
     */
    private String sceneType;

    /**
     * 关联的剧本ID
     */
    private String scriptId;

    /**
     * 场景名称
     */
    private String name;

    /**
     * 场景描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    private String fixedDesc;

    /**
     * 外观数据 (JSON)
     * 包含：环境设定(时间、天气、地点类型)、氛围设定(光线、色调、情绪)等
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
