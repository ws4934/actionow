package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 角色版本详情响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterVersionDetailResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 角色ID
     */
    private String characterId;

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

    // ========== 角色业务字段快照 ==========

    /**
     * 作用域: WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID
     */
    private String scriptId;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
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
     * 角色类型: PROTAGONIST, ANTAGONIST, SUPPORTING, BACKGROUND
     */
    private String characterType;

    /**
     * 语音种子ID（用于TTS）
     */
    private String voiceSeedId;

    /**
     * 外貌数据
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
