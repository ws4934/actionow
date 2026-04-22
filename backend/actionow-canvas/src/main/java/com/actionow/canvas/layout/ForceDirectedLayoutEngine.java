package com.actionow.canvas.layout;

import com.actionow.canvas.constant.CanvasConstants;
import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.canvas.entity.CanvasNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 力导向布局引擎
 * 实现 Fruchterman-Reingold 力导向算法
 * 节点之间存在斥力，连接的节点之间存在引力
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ForceDirectedLayoutEngine implements LayoutEngine {

    @Override
    public void applyLayout(List<CanvasNode> nodes, List<CanvasEdge> edges, LayoutConfig config) {
        if (nodes == null || nodes.size() < 2) {
            // 单节点或空节点无需力导向布局
            if (nodes != null && nodes.size() == 1) {
                CanvasNode node = nodes.get(0);
                node.setPositionX(BigDecimal.valueOf(config.getCenterX()));
                node.setPositionY(BigDecimal.valueOf(config.getCenterY()));
            }
            return;
        }

        int n = nodes.size();

        // 初始化节点位置（圆形分布）
        initializePositions(nodes, config);

        // 构建边的索引映射
        Map<String, Set<String>> adjacencyMap = buildAdjacencyMap(nodes, edges);

        // 速度数组
        double[] vx = new double[n];
        double[] vy = new double[n];
        Arrays.fill(vx, 0);
        Arrays.fill(vy, 0);

        // 迭代计算
        for (int iter = 0; iter < config.getIterations(); iter++) {
            // 计算斥力（所有节点对）
            double[][] repulsionForces = calculateRepulsionForces(nodes, config);

            // 计算引力（仅连接的节点）
            double[][] attractionForces = calculateAttractionForces(nodes, edges, adjacencyMap, config);

            // 计算中心引力
            double[][] centerForces = calculateCenterGravity(nodes, config);

            // 更新速度和位置
            double maxVelocity = 0;
            double temperature = config.getDamping() * (1.0 - (double) iter / config.getIterations());

            for (int i = 0; i < n; i++) {
                // 合并力
                double fx = repulsionForces[i][0] + attractionForces[i][0] + centerForces[i][0];
                double fy = repulsionForces[i][1] + attractionForces[i][1] + centerForces[i][1];

                // 更新速度（带阻尼）
                vx[i] = (vx[i] + fx) * temperature;
                vy[i] = (vy[i] + fy) * temperature;

                // 限制最大速度
                double velocity = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
                double maxSpeed = 50.0;
                if (velocity > maxSpeed) {
                    vx[i] = (vx[i] / velocity) * maxSpeed;
                    vy[i] = (vy[i] / velocity) * maxSpeed;
                    velocity = maxSpeed;
                }

                maxVelocity = Math.max(maxVelocity, velocity);

                // 更新位置
                CanvasNode node = nodes.get(i);
                double newX = node.getPositionX().doubleValue() + vx[i];
                double newY = node.getPositionY().doubleValue() + vy[i];

                // 边界约束
                newX = Math.max(0, Math.min(newX, config.getCenterX() * 2));
                newY = Math.max(0, Math.min(newY, config.getCenterY() * 2));

                node.setPositionX(BigDecimal.valueOf(Math.round(newX)));
                node.setPositionY(BigDecimal.valueOf(Math.round(newY)));
            }

            // 收敛检测
            if (maxVelocity < config.getMinVelocityThreshold()) {
                log.debug("FORCE 布局在第 {} 次迭代时收敛", iter);
                break;
            }
        }

        // 设置 z-index
        for (int i = 0; i < n; i++) {
            nodes.get(i).setZIndex(i);
        }

        log.info("FORCE 布局完成: nodeCount={}, edgeCount={}", nodes.size(), edges != null ? edges.size() : 0);
    }

    @Override
    public String getStrategy() {
        return CanvasConstants.LayoutStrategy.FORCE;
    }

    /**
     * 初始化节点位置（圆形分布）
     */
    private void initializePositions(List<CanvasNode> nodes, LayoutConfig config) {
        int n = nodes.size();
        double radius = Math.min(config.getCenterX(), config.getCenterY()) * 0.8;

        for (int i = 0; i < n; i++) {
            CanvasNode node = nodes.get(i);
            double angle = 2 * Math.PI * i / n;
            double x = config.getCenterX() + radius * Math.cos(angle);
            double y = config.getCenterY() + radius * Math.sin(angle);
            node.setPositionX(BigDecimal.valueOf(Math.round(x)));
            node.setPositionY(BigDecimal.valueOf(Math.round(y)));
        }
    }

    /**
     * 构建邻接映射
     */
    private Map<String, Set<String>> buildAdjacencyMap(List<CanvasNode> nodes, List<CanvasEdge> edges) {
        Map<String, Set<String>> adjacencyMap = new HashMap<>();

        // 初始化所有节点
        for (CanvasNode node : nodes) {
            String key = node.getEntityType() + ":" + node.getEntityId();
            adjacencyMap.put(key, new HashSet<>());
        }

        // 添加边的连接关系
        if (edges != null) {
            for (CanvasEdge edge : edges) {
                String sourceKey = edge.getSourceType() + ":" + edge.getSourceId();
                String targetKey = edge.getTargetType() + ":" + edge.getTargetId();

                adjacencyMap.computeIfAbsent(sourceKey, k -> new HashSet<>()).add(targetKey);
                adjacencyMap.computeIfAbsent(targetKey, k -> new HashSet<>()).add(sourceKey);
            }
        }

        return adjacencyMap;
    }

    /**
     * 计算斥力（Coulomb's Law）
     */
    private double[][] calculateRepulsionForces(List<CanvasNode> nodes, LayoutConfig config) {
        int n = nodes.size();
        double[][] forces = new double[n][2];

        for (int i = 0; i < n; i++) {
            CanvasNode nodeI = nodes.get(i);
            double xi = nodeI.getPositionX().doubleValue();
            double yi = nodeI.getPositionY().doubleValue();

            for (int j = i + 1; j < n; j++) {
                CanvasNode nodeJ = nodes.get(j);
                double xj = nodeJ.getPositionX().doubleValue();
                double yj = nodeJ.getPositionY().doubleValue();

                double dx = xi - xj;
                double dy = yi - yj;
                double distance = Math.sqrt(dx * dx + dy * dy);

                // 避免除零
                if (distance < 1) {
                    distance = 1;
                    dx = Math.random() - 0.5;
                    dy = Math.random() - 0.5;
                }

                // 斥力 = k / d^2
                double force = config.getRepulsionStrength() / (distance * distance);
                double fx = (dx / distance) * force;
                double fy = (dy / distance) * force;

                forces[i][0] += fx;
                forces[i][1] += fy;
                forces[j][0] -= fx;
                forces[j][1] -= fy;
            }
        }

        return forces;
    }

    /**
     * 计算引力（Hooke's Law）
     */
    private double[][] calculateAttractionForces(List<CanvasNode> nodes, List<CanvasEdge> edges,
                                                   Map<String, Set<String>> adjacencyMap, LayoutConfig config) {
        int n = nodes.size();
        double[][] forces = new double[n][2];

        if (edges == null || edges.isEmpty()) {
            return forces;
        }

        // 构建节点索引映射
        Map<String, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            CanvasNode node = nodes.get(i);
            String key = node.getEntityType() + ":" + node.getEntityId();
            nodeIndex.put(key, i);
        }

        // 计算边的引力
        for (CanvasEdge edge : edges) {
            String sourceKey = edge.getSourceType() + ":" + edge.getSourceId();
            String targetKey = edge.getTargetType() + ":" + edge.getTargetId();

            Integer sourceIdx = nodeIndex.get(sourceKey);
            Integer targetIdx = nodeIndex.get(targetKey);

            if (sourceIdx == null || targetIdx == null) {
                continue;
            }

            CanvasNode source = nodes.get(sourceIdx);
            CanvasNode target = nodes.get(targetIdx);

            double dx = target.getPositionX().doubleValue() - source.getPositionX().doubleValue();
            double dy = target.getPositionY().doubleValue() - source.getPositionY().doubleValue();
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < 1) {
                continue;
            }

            // 引力 = k * (d - ideal_length)
            double displacement = distance - config.getIdealEdgeLength();
            double force = config.getAttractionStrength() * displacement;
            double fx = (dx / distance) * force;
            double fy = (dy / distance) * force;

            forces[sourceIdx][0] += fx;
            forces[sourceIdx][1] += fy;
            forces[targetIdx][0] -= fx;
            forces[targetIdx][1] -= fy;
        }

        return forces;
    }

    /**
     * 计算中心引力
     */
    private double[][] calculateCenterGravity(List<CanvasNode> nodes, LayoutConfig config) {
        int n = nodes.size();
        double[][] forces = new double[n][2];

        for (int i = 0; i < n; i++) {
            CanvasNode node = nodes.get(i);
            double dx = config.getCenterX() - node.getPositionX().doubleValue();
            double dy = config.getCenterY() - node.getPositionY().doubleValue();

            forces[i][0] = dx * config.getCenterGravity();
            forces[i][1] = dy * config.getCenterGravity();
        }

        return forces;
    }
}
