package com.actionow.canvas.controller;

import com.actionow.canvas.dto.edge.CanvasEdgeResponse;
import com.actionow.canvas.dto.edge.CreateEdgeRequest;
import com.actionow.canvas.dto.edge.UpdateEdgeRequest;
import com.actionow.canvas.service.CanvasEdgeService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 画布边控制器
 * 管理画布中实体之间的连线关系
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/canvas/edges")
@RequiredArgsConstructor
public class CanvasEdgeController {

    private final CanvasEdgeService edgeService;

    /**
     * 创建边
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<CanvasEdgeResponse> createEdge(@RequestBody @Valid CreateEdgeRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        CanvasEdgeResponse response = edgeService.createEdge(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 批量创建边
     */
    @PostMapping("/batch")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<List<CanvasEdgeResponse>> batchCreateEdges(@RequestBody @Valid List<CreateEdgeRequest> requests) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        List<CanvasEdgeResponse> responses = edgeService.batchCreateEdges(requests, workspaceId, userId);
        return Result.success(responses);
    }

    /**
     * 获取边详情
     */
    @GetMapping("/{edgeId}")
    @RequireWorkspaceMember
    public Result<CanvasEdgeResponse> getEdge(@PathVariable String edgeId) {
        CanvasEdgeResponse response = edgeService.getEdge(edgeId);
        return Result.success(response);
    }

    /**
     * 获取画布中的所有边
     */
    @GetMapping("/canvas/{canvasId}")
    @RequireWorkspaceMember
    public Result<List<CanvasEdgeResponse>> listByCanvasId(@PathVariable String canvasId) {
        List<CanvasEdgeResponse> edges = edgeService.listByCanvasId(canvasId);
        return Result.success(edges);
    }

    /**
     * 更新边
     */
    @PutMapping("/{edgeId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<CanvasEdgeResponse> updateEdge(
            @PathVariable String edgeId,
            @RequestBody @Valid UpdateEdgeRequest request) {
        String userId = UserContextHolder.getUserId();
        CanvasEdgeResponse response = edgeService.updateEdge(edgeId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除边
     */
    @DeleteMapping("/{edgeId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteEdge(@PathVariable String edgeId) {
        String userId = UserContextHolder.getUserId();
        edgeService.deleteEdge(edgeId, userId);
        return Result.success();
    }

    /**
     * 验证边是否允许
     */
    @GetMapping("/validate")
    @RequireWorkspaceMember
    public Result<Boolean> validateEdge(
            @RequestParam String canvasId,
            @RequestParam String sourceType,
            @RequestParam String sourceId,
            @RequestParam String targetType,
            @RequestParam String targetId) {
        boolean valid = edgeService.validateEdge(canvasId,
                sourceType.toUpperCase(), sourceId,
                targetType.toUpperCase(), targetId);
        return Result.success(valid);
    }

    /**
     * 推断关系类型
     */
    @GetMapping("/infer-relation")
    @RequireWorkspaceMember
    public Result<String> inferRelationType(
            @RequestParam String sourceType,
            @RequestParam String targetType) {
        String relationType = edgeService.inferRelationType(
                sourceType.toUpperCase(), targetType.toUpperCase());
        return Result.success(relationType);
    }
}
