package com.actionow.canvas.layout;

import com.actionow.canvas.constant.CanvasConstants;
import lombok.Builder;
import lombok.Data;

/**
 * 布局配置
 *
 * @author Actionow
 */
@Data
@Builder
public class LayoutConfig {

    /**
     * 节点默认宽度
     */
    @Builder.Default
    private int nodeWidth = CanvasConstants.LayoutDefaults.NODE_WIDTH;

    /**
     * 节点默认高度
     */
    @Builder.Default
    private int nodeHeight = CanvasConstants.LayoutDefaults.NODE_HEIGHT;

    /**
     * 水平间距
     */
    @Builder.Default
    private int gapX = CanvasConstants.LayoutDefaults.GAP_X;

    /**
     * 垂直间距
     */
    @Builder.Default
    private int gapY = CanvasConstants.LayoutDefaults.GAP_Y;

    /**
     * 网格列数
     */
    @Builder.Default
    private int columns = CanvasConstants.LayoutDefaults.COLUMNS;

    // ==================== FORCE 布局参数 ====================

    /**
     * 斥力系数（节点之间的排斥力）
     */
    @Builder.Default
    private double repulsionStrength = 5000.0;

    /**
     * 引力系数（连接节点之间的吸引力）
     */
    @Builder.Default
    private double attractionStrength = 0.01;

    /**
     * 理想边长度
     */
    @Builder.Default
    private double idealEdgeLength = 200.0;

    /**
     * 中心引力（将节点拉向中心）
     */
    @Builder.Default
    private double centerGravity = 0.1;

    /**
     * 迭代次数
     */
    @Builder.Default
    private int iterations = 300;

    /**
     * 阻尼系数（防止震荡）
     */
    @Builder.Default
    private double damping = 0.9;

    /**
     * 最小速度阈值（低于此值停止迭代）
     */
    @Builder.Default
    private double minVelocityThreshold = 0.1;

    /**
     * 画布中心X
     */
    @Builder.Default
    private double centerX = 500.0;

    /**
     * 画布中心Y
     */
    @Builder.Default
    private double centerY = 400.0;

    /**
     * 创建默认配置
     */
    public static LayoutConfig defaults() {
        return LayoutConfig.builder().build();
    }
}
