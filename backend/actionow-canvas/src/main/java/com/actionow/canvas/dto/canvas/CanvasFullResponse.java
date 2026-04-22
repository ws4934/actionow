package com.actionow.canvas.dto.canvas;

import com.actionow.canvas.dto.edge.CanvasEdgeResponse;
import com.actionow.canvas.dto.node.CanvasNodeResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 画布完整响应（包含节点和边）
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CanvasFullResponse extends CanvasResponse {

    /**
     * 画布节点列表
     */
    private List<CanvasNodeResponse> nodes;

    /**
     * 画布边列表
     */
    private List<CanvasEdgeResponse> edges;
}
