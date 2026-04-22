package com.actionow.project.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量更新实体请求
 * Canvas 批量更新节点时使用
 *
 * @author Actionow
 */
@Data
public class BatchEntityUpdateRequest {

    /**
     * 更新请求列表
     */
    private List<CanvasEntityUpdateRequest> requests;

    /**
     * 是否使用事务
     * true: 全部成功或全部失败
     * false: 尽可能多地更新，返回每个的结果
     */
    private Boolean transactional = false;
}
