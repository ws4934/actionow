package com.actionow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 创建场景请求
 *
 * @author Actionow
 */
@Data
public class CreateSceneRequest {

    /**
     * 作用域: SYSTEM-系统预置, WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope = "WORKSPACE";

    /**
     * 场景类型: INTERIOR-室内, EXTERIOR-室外, MIXED-混合
     */
    private String sceneType;

    /**
     * 关联的剧本ID（scope为SCRIPT时必填）
     */
    private String scriptId;

    /**
     * 场景名称
     */
    @NotBlank(message = "场景名称不能为空")
    @Size(min = 1, max = 100, message = "场景名称长度为1-100个字符")
    private String name;

    /**
     * 场景描述
     */
    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    /**
     * 固定描述词
     */
    @Size(max = 500, message = "固定描述词不能超过500个字符")
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
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 保存模式（仅更新时有效）
     * OVERWRITE - 覆盖当前版本（不创建版本快照）
     * NEW_VERSION - 存为新版本（默认，推荐）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;
}
