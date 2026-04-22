package com.actionow.canvas.layout;

import com.actionow.canvas.constant.CanvasConstants;
import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.canvas.entity.CanvasNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网格布局引擎
 * 将节点按照网格排列
 *
 * @author Actionow
 */
@Slf4j
@Component
public class GridLayoutEngine implements LayoutEngine {

    @Override
    public void applyLayout(List<CanvasNode> nodes, List<CanvasEdge> edges, LayoutConfig config) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        int columns = config.getColumns();
        int nodeWidth = config.getNodeWidth();
        int nodeHeight = config.getNodeHeight();
        int gapX = config.getGapX();
        int gapY = config.getGapY();

        for (int i = 0; i < nodes.size(); i++) {
            CanvasNode node = nodes.get(i);
            int row = i / columns;
            int col = i % columns;
            node.setPositionX(BigDecimal.valueOf((long) col * (nodeWidth + gapX)));
            node.setPositionY(BigDecimal.valueOf((long) row * (nodeHeight + gapY)));
            node.setZIndex(i);
        }

        log.info("GRID 布局完成: nodeCount={}", nodes.size());
    }

    @Override
    public String getStrategy() {
        return CanvasConstants.LayoutStrategy.GRID;
    }
}
