package com.actionow.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新剧本请求
 * 所有字段均为可选，只更新传入的非空字段
 *
 * @author Actionow
 */
@Data
public class UpdateScriptRequest {

    /**
     * 剧本标题
     */
    @Size(min = 1, max = 200, message = "剧本标题长度为1-200个字符")
    private String title;

    /**
     * 剧本简介
     */
    @Size(max = 2000, message = "简介不能超过2000个字符")
    private String synopsis;

    /**
     * 剧本类型: ORIGINAL, ADAPTED
     */
    private String scriptType;

    /**
     * 风格ID列表
     */
    private java.util.List<String> styleIds;

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
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED
     */
    private String status;

    /**
     * 保存模式
     * OVERWRITE - 覆盖当前版本
     * NEW_VERSION - 存为新版本（默认）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;
}
