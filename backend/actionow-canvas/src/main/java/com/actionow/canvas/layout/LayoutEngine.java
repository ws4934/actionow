package com.actionow.canvas.layout;

import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.canvas.entity.CanvasNode;

import java.util.List;

/**
 * 布局引擎接口
 * 定义画布自动布局的标准接口
 *
 * @author Actionow
 */
public interface LayoutEngine {

    /**
     * 对节点应用布局
     *
     * @param nodes  节点列表
     * @param edges  边列表（用于关系感知布局）
     * @param config 布局配置
     */
    void applyLayout(List<CanvasNode> nodes, List<CanvasEdge> edges, LayoutConfig config);

    /**
     * 获取布局策略名称
     */
    String getStrategy();
}
