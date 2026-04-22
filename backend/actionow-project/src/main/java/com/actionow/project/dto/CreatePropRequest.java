package com.actionow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 创建道具请求
 *
 * @author Actionow
 */
@Data
public class CreatePropRequest {

    /**
     * 作用域
     */
    private String scope = "WORKSPACE";

    /**
     * 关联的剧本ID（scope为SCRIPT时必填）
     */
    private String scriptId;

    /**
     * 道具名称
     */
    @NotBlank(message = "道具名称不能为空")
    @Size(min = 1, max = 100, message = "道具名称长度为1-100个字符")
    private String name;

    /**
     * 道具描述
     */
    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    /**
     * 固定描述词
     */
    @Size(max = 500, message = "固定描述词不能超过500个字符")
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
