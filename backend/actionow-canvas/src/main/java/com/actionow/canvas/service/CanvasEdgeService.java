package com.actionow.canvas.service;

import com.actionow.canvas.dto.edge.CanvasEdgeResponse;
import com.actionow.canvas.dto.edge.CreateEdgeRequest;
import com.actionow.canvas.dto.edge.UpdateEdgeRequest;

import java.util.List;

/**
 * 画布边服务接口
 *
 * @author Actionow
 */
public interface CanvasEdgeService {

    /**
     * 创建边
     */
    CanvasEdgeResponse createEdge(CreateEdgeRequest request, String workspaceId, String userId);

    /**
     * 批量创建边
     */
    List<CanvasEdgeResponse> batchCreateEdges(List<CreateEdgeRequest> requests, String workspaceId, String userId);

    /**
     * 获取边
     */
    CanvasEdgeResponse getEdge(String edgeId);

    /**
     * 根据画布ID查询所有边
     */
    List<CanvasEdgeResponse> listByCanvasId(String canvasId);

    /**
     * 更新边
     */
    CanvasEdgeResponse updateEdge(String edgeId, UpdateEdgeRequest request, String userId);

    /**
     * 删除边
     */
    void deleteEdge(String edgeId, String userId);

    /**
     * 删除画布下所有边
     */
    void deleteByCanvasId(String canvasId);

    /**
     * 验证边是否允许
     */
    boolean validateEdge(String canvasId, String sourceType, String sourceId,
                         String targetType, String targetId);

    /**
     * 推断关系类型
     */
    String inferRelationType(String sourceType, String targetType);

    /**
     * 删除与实体相关的所有边
     */
    void deleteByEntity(String entityType, String entityId);
}
