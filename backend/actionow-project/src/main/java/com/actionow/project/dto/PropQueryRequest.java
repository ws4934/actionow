package com.actionow.project.dto;

import lombok.Data;

/**
 * 道具查询请求（支持分页和过滤）
 *
 * @author Actionow
 */
@Data
public class PropQueryRequest {

    /**
     * 作用域（SYSTEM, WORKSPACE, SCRIPT）
     */
    private String scope;

    /**
     * 剧本ID（scope为SCRIPT时有效）
     */
    private String scriptId;

    /**
     * 道具类型
     */
    private String propType;

    /**
     * 关键词搜索（匹配名称和描述）
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
     * 排序字段（created_at, updated_at, name）
     */
    private String orderBy = "created_at";

    /**
     * 排序方向（asc, desc）
     */
    private String orderDir = "desc";
}
