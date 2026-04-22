package com.actionow.canvas.dto.edge;

import lombok.Data;

import java.util.Map;

/**
 * 更新画布边请求
 *
 * @author Actionow
 */
@Data
public class UpdateEdgeRequest {

    /**
     * 关系标签
     */
    private String relationLabel;

    /**
     * 描述
     */
    private String description;

    /**
     * 源节点连接点
     */
    private String sourceHandle;

    /**
     * 目标节点连接点
     */
    private String targetHandle;

    /**
     * 线条样式
     */
    private Map<String, Object> lineStyle;

    /**
     * 路径类型
     */
    private String pathType;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
