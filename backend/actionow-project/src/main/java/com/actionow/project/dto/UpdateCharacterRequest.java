package com.actionow.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新角色请求
 * 所有字段均为可选，只更新传入的非空字段
 *
 * @author Actionow
 */
@Data
public class UpdateCharacterRequest {

    /**
     * 角色名称
     */
    @Size(min = 1, max = 100, message = "角色名称长度为1-100个字符")
    private String name;

    /**
     * 角色描述
     */
    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    @Size(max = 500, message = "固定描述词不能超过500个字符")
    private String fixedDesc;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 性别: MALE, FEMALE, OTHER
     */
    private String gender;

    /**
     * 角色类型: PROTAGONIST, ANTAGONIST, SUPPORTING, BACKGROUND
     */
    private String characterType;

    /**
     * 外貌数据 (JSON)
     */
    private Map<String, Object> appearanceData;

    /**
     * 性格特点
     */
    private String personality;

    /**
     * 背景故事
     */
    private String backstory;

    /**
     * 语音种子ID
     */
    private String voiceSeedId;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 外貌数据增量补丁 (JSON)
     * 与现有 appearanceData 做 merge（putAll），不会覆盖未传字段
     * 优先级高于 appearanceData（如果同时传入，以 patch 为准）
     */
    private Map<String, Object> appearanceDataPatch;

    /**
     * 扩展信息增量补丁 (JSON)
     * 与现有 extraInfo 做 merge（putAll），不会覆盖未传字段
     * 优先级高于 extraInfo（如果同时传入，以 patch 为准）
     */
    private Map<String, Object> extraInfoPatch;

    /**
     * 保存模式
     * OVERWRITE - 覆盖当前版本
     * NEW_VERSION - 存为新版本（默认）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;
}
