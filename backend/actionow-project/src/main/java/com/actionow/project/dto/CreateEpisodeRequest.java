package com.actionow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 创建剧集请求
 *
 * @author Actionow
 */
@Data
public class CreateEpisodeRequest {

    /**
     * 所属剧本ID
     */
    private String scriptId;

    /**
     * 剧集标题
     */
    @NotBlank(message = "剧集标题不能为空")
    @Size(min = 1, max = 200, message = "剧集标题长度为1-200个字符")
    private String title;

    /**
     * 剧集简介
     */
    @Size(max = 2000, message = "简介不能超过2000个字符")
    private String synopsis;

    /**
     * 剧集内容
     */
    private String content;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 文档素材ID
     */
    private String docAssetId;

    /**
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED
     * 仅更新时有效
     */
    private String status;

    /**
     * 指定序号（可选，不指定则追加到末尾）
     */
    private Integer sequence;

    /**
     * 保存模式（仅更新时有效）
     * OVERWRITE - 覆盖当前版本（不创建版本快照）
     * NEW_VERSION - 存为新版本（默认，推荐）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;
}
