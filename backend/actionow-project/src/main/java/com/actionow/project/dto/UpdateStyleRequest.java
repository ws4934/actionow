package com.actionow.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新风格请求
 * 所有字段均为可选，只更新传入的非空字段
 *
 * @author Actionow
 */
@Data
public class UpdateStyleRequest {

    /**
     * 风格名称
     */
    @Size(min = 1, max = 100, message = "风格名称长度为1-100个字符")
    private String name;

    /**
     * 风格描述
     */
    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    @Size(max = 1000, message = "固定描述词不能超过1000个字符")
    private String fixedDesc;

    /**
     * 负面提示词
     */
    @Size(max = 1000, message = "负面提示词不能超过1000个字符")
    private String negativePrompt;

    /**
     * 风格标签
     */
    private java.util.List<String> tags;

    /**
     * 风格参数 (JSON)
     */
    private Map<String, Object> styleParams;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 保存模式
     * OVERWRITE - 覆盖当前版本
     * NEW_VERSION - 存为新版本（默认）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;
}
