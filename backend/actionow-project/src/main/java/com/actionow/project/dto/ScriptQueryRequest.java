package com.actionow.project.dto;

import lombok.Data;

/**
 * 剧本查询请求（支持分页和过滤）
 *
 * @author Actionow
 */
@Data
public class ScriptQueryRequest {

    /**
     * 状态（DRAFT, IN_PROGRESS, COMPLETED, ARCHIVED）
     */
    private String status;

    /**
     * 关键词搜索（匹配标题和简介）
     */
    private String keyword;

    /**
     * 创建者ID
     */
    private String createdBy;

    /**
     * 页码（从1开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 20;

    /**
     * 排序字段（created_at, updated_at, title）
     */
    private String orderBy = "created_at";

    /**
     * 排序方向（asc, desc）
     */
    private String orderDir = "desc";
}
