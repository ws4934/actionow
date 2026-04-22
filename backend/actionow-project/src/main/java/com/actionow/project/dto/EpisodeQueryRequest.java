package com.actionow.project.dto;

import lombok.Data;

/**
 * 剧集查询请求（支持分页和过滤）
 *
 * @author Actionow
 */
@Data
public class EpisodeQueryRequest {

    /**
     * 剧本ID（必填）
     */
    private String scriptId;

    /**
     * 状态（DRAFT, IN_PROGRESS, COMPLETED）
     */
    private String status;

    /**
     * 关键词搜索（匹配标题和简介）
     */
    private String keyword;

    /**
     * 页码（从1开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 20;

    /**
     * 排序字段（created_at, updated_at, sequence, title）
     */
    private String orderBy = "sequence";

    /**
     * 排序方向（asc, desc）
     */
    private String orderDir = "asc";
}
