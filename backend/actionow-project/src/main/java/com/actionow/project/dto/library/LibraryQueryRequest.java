package com.actionow.project.dto.library;

import lombok.Data;

/**
 * 公共库通用查询请求
 *
 * @author Actionow
 */
@Data
public class LibraryQueryRequest {

    /** 关键词（名称/描述模糊搜索）*/
    private String keyword;

    /** 页码，从 1 开始 */
    private int pageNum = 1;

    /** 每页大小 */
    private int pageSize = 20;

    /** 排序字段: publishedAt(默认) | name */
    private String orderBy = "publishedAt";

    /** 排序方向: desc(默认) | asc */
    private String orderDir = "desc";
}
