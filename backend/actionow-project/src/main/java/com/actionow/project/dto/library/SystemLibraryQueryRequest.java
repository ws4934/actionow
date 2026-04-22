package com.actionow.project.dto.library;

import lombok.Data;

/**
 * 系统管理员查询请求（可查看草稿和已发布资源）
 *
 * @author Actionow
 */
@Data
public class SystemLibraryQueryRequest {

    private String keyword;

    /**
     * scope 过滤: WORKSPACE(草稿) | SYSTEM(已发布) | 不传=全部
     */
    private String scope;

    private int pageNum = 1;
    private int pageSize = 20;
    private String orderBy = "createdAt";
    private String orderDir = "desc";
}
