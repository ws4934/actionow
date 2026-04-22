package com.actionow.project.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 素材查询请求 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetQueryRequest {

    /**
     * 剧本ID
     */
    private String scriptId;

    /**
     * 素材类型
     */
    private String assetType;

    /**
     * 来源
     */
    private String source;

    /**
     * 生成状态
     */
    private String generationStatus;

    /**
     * 作用域
     */
    private String scope;

    /**
     * 关键词搜索
     */
    private String keyword;

    /**
     * 页码
     */
    private Integer page;

    /**
     * 每页数量
     */
    private Integer size;
}
