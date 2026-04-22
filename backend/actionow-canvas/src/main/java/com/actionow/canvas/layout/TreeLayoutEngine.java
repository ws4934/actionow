package com.actionow.canvas.layout;

import com.actionow.canvas.constant.CanvasConstants;
import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.canvas.entity.CanvasNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 树形布局引擎
 * 基于边关系构建层次结构，自动计算父子层级
 *
 * @author Actionow
 */
@Slf4j
@Component
public class TreeLayoutEngine implements LayoutEngine {

    @Override
    public void applyLayout(List<CanvasNode> nodes, List<CanvasEdge> edges, LayoutConfig config) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        int nodeWidth = config.getNodeWidth();
        int nodeHeight = config.getNodeHeight();
        int gapX = config.getGapX();
        int gapY = config.getGapY() * 2;

        if (edges == null || edges.isEmpty()) {
            // 无边时使用简单水平布局
            applySimpleLayout(nodes, nodeWidth, gapX);
            log.info("TREE 布局完成（无边，水平排列）: nodeCount={}", nodes.size());
            return;
        }

        // 构建节点映射和子节点关系
        Map<String, CanvasNode> nodeMap = buildNodeMap(nodes);
        Map<String, List<String>> childrenMap = buildChildrenMap(edges);
        Set<String> childNodes = getChildNodes(edges);

        // 找到根节点（没有入边的节点）
        List<String> roots = findRootNodes(nodes, childNodes);

        if (roots.isEmpty()) {
            // 如果没有明确的根节点，选择第一个节点作为根
            roots.add(nodes.get(0).getEntityType() + ":" + nodes.get(0).getEntityId());
        }

        // 计算每个节点的层级
        Map<String, Integer> levels = calculateLevels(roots, childrenMap);

        // 按层级分组
        Map<Integer, List<String>> nodesByLevel = groupByLevel(levels);

        // 计算每层的偏移，使树居中
        int maxNodesInLevel = nodesByLevel.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(1);

        // 为每个节点设置位置
        for (Map.Entry<Integer, List<String>> entry : nodesByLevel.entrySet()) {
            int level = entry.getKey();
            List<String> nodesAtLevel = entry.getValue();
            int levelWidth = nodesAtLevel.size() * (nodeWidth + gapX) - gapX;
            int maxWidth = maxNodesInLevel * (nodeWidth + gapX) - gapX;
            int startX = (maxWidth - levelWidth) / 2;

            for (int i = 0; i < nodesAtLevel.size(); i++) {
                String nodeKey = nodesAtLevel.get(i);
                CanvasNode node = nodeMap.get(nodeKey);
                if (node != null) {
                    node.setPositionX(BigDecimal.valueOf(startX + i * (nodeWidth + gapX)));
                    node.setPositionY(BigDecimal.valueOf((long) level * (nodeHeight + gapY)));
                    node.setZIndex(level * 100 + i);
                }
            }
        }

        // 处理未分配层级的节点（孤立节点）
        int orphanIndex = 0;
        int maxLevel = levels.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        for (CanvasNode node : nodes) {
            String key = node.getEntityType() + ":" + node.getEntityId();
            if (!levels.containsKey(key)) {
                node.setPositionX(BigDecimal.valueOf((long) orphanIndex * (nodeWidth + gapX)));
                node.setPositionY(BigDecimal.valueOf((long) maxLevel * (nodeHeight + gapY)));
                node.setZIndex(maxLevel * 100 + orphanIndex);
                orphanIndex++;
            }
        }

        log.info("TREE 布局完成: nodeCount={}, levels={}", nodes.size(), nodesByLevel.size());
    }

    @Override
    public String getStrategy() {
        return CanvasConstants.LayoutStrategy.TREE;
    }

    private void applySimpleLayout(List<CanvasNode> nodes, int nodeWidth, int gapX) {
        for (int i = 0; i < nodes.size(); i++) {
            CanvasNode node = nodes.get(i);
            node.setPositionX(BigDecimal.valueOf((long) i * (nodeWidth + gapX)));
            node.setPositionY(BigDecimal.valueOf(100));
            node.setZIndex(i);
        }
    }

    private Map<String, CanvasNode> buildNodeMap(List<CanvasNode> nodes) {
        Map<String, CanvasNode> map = new HashMap<>();
        for (CanvasNode node : nodes) {
            String key = node.getEntityType() + ":" + node.getEntityId();
            map.put(key, node);
        }
        return map;
    }

    private Map<String, List<String>> buildChildrenMap(List<CanvasEdge> edges) {
        Map<String, List<String>> map = new HashMap<>();
        for (CanvasEdge edge : edges) {
            String sourceKey = edge.getSourceType() + ":" + edge.getSourceId();
            String targetKey = edge.getTargetType() + ":" + edge.getTargetId();
            map.computeIfAbsent(sourceKey, k -> new ArrayList<>()).add(targetKey);
        }
        return map;
    }

    private Set<String> getChildNodes(List<CanvasEdge> edges) {
        Set<String> children = new HashSet<>();
        for (CanvasEdge edge : edges) {
            String targetKey = edge.getTargetType() + ":" + edge.getTargetId();
            children.add(targetKey);
        }
        return children;
    }

    private List<String> findRootNodes(List<CanvasNode> nodes, Set<String> childNodes) {
        List<String> roots = new ArrayList<>();
        for (CanvasNode node : nodes) {
            String key = node.getEntityType() + ":" + node.getEntityId();
            if (!childNodes.contains(key)) {
                roots.add(key);
            }
        }
        return roots;
    }

    private Map<String, Integer> calculateLevels(List<String> roots, Map<String, List<String>> childrenMap) {
        Map<String, Integer> levels = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        for (String root : roots) {
            queue.offer(root);
            levels.put(root, 0);
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentLevel = levels.get(current);

            List<String> children = childrenMap.get(current);
            if (children != null) {
                for (String child : children) {
                    if (!levels.containsKey(child)) {
                        levels.put(child, currentLevel + 1);
                        queue.offer(child);
                    }
                }
            }
        }

        return levels;
    }

    private Map<Integer, List<String>> groupByLevel(Map<String, Integer> levels) {
        Map<Integer, List<String>> grouped = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        return grouped;
    }
}
