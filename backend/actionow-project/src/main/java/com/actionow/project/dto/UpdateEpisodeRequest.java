package com.actionow.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新剧集请求
 * 所有字段均为可选，只更新传入的非空字段
 *
 * @author Actionow
 */
@Data
public class UpdateEpisodeRequest {

    /**
     * 剧集标题
     */
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
     * 扩展信息补丁（merge 语义）
     * 与现有 extraInfo 合并，不会覆盖未传的字段
     */
    private Map<String, Object> extraInfoPatch;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED
     */
    private String status;

    /**
     * 序号
     */
    private Integer sequence;

    /**
     * 保存模式
     * OVERWRITE - 覆盖当前版本（不创建版本快照）
     * NEW_VERSION - 存为新版本（默认，推荐）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;
}
