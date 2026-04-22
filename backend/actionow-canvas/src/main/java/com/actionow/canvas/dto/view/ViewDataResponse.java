package com.actionow.canvas.dto.view;

import com.actionow.canvas.dto.edge.CanvasEdgeResponse;
import com.actionow.canvas.dto.node.CanvasNodeResponse;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 视图数据响应
 * 返回特定视图下的节点和边数据
 *
 * @author Actionow
 */
@Data
public class ViewDataResponse {

    /**
     * 画布ID
     */
    private String canvasId;

    /**
     * 视图键
     */
    private String viewKey;

    /**
     * 视图名称
     */
    private String viewName;

    /**
     * 是否为聚焦模式
     * true: 显示某个特定实体及其关联节点
     * false: 显示视图下所有节点
     */
    private Boolean focusMode = false;

    /**
     * 聚焦实体类型（聚焦模式时有值）
     */
    private String focusEntityType;

    /**
     * 聚焦实体ID（聚焦模式时有值）
     */
    private String focusEntityId;

    /**
     * 聚焦实体名称（聚焦模式时有值，方便前端展示）
     */
    private String focusEntityName;

    /**
     * 节点列表
     */
    private List<CanvasNodeResponse> nodes;

    /**
     * 边列表
     */
    private List<CanvasEdgeResponse> edges;

    /**
     * 视口状态
     */
    private Map<String, Object> viewport;

    /**
     * 节点总数
     */
    private Integer totalNodes;

    /**
     * 边总数
     */
    private Integer totalEdges;

    /**
     * 各实体类型的节点数量统计
     */
    private Map<String, Integer> nodeCountByType;
}
