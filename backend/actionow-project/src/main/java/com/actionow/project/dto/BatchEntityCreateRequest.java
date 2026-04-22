package com.actionow.project.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量创建实体请求
 * Canvas 批量创建节点时使用
 *
 * @author Actionow
 */
@Data
public class BatchEntityCreateRequest {

    /**
     * 创建请求列表
     */
    private List<CanvasEntityCreateRequest> requests;

    /**
     * 是否使用事务
     * true: 全部成功或全部失败
     * false: 尽可能多地创建，返回每个的结果
     */
    private Boolean transactional = false;
}
