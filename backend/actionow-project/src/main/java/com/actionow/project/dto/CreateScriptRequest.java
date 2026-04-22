package com.actionow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 创建剧本请求
 *
 * @author Actionow
 */
@Data
public class CreateScriptRequest {

    /**
     * 剧本标题
     */
    @NotBlank(message = "剧本标题不能为空")
    @Size(min = 1, max = 200, message = "剧本标题长度为1-200个字符")
    private String title;

    /**
     * 剧本简介
     */
    @Size(max = 2000, message = "简介不能超过2000个字符")
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
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED, ARCHIVED
     * 仅更新时有效
     */
    private String status;

    /**
     * 保存模式（仅更新时有效）
     * OVERWRITE - 覆盖当前版本（不创建版本快照）
     * NEW_VERSION - 存为新版本（默认，推荐）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;
}
