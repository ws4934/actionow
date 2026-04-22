package com.actionow.canvas.controller;

import com.actionow.canvas.dto.view.CreateViewRequest;
import com.actionow.canvas.dto.view.UpdateViewRequest;
import com.actionow.canvas.dto.view.ViewResponse;
import com.actionow.canvas.service.CanvasViewService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 画布视图控制器
 * 管理画布的视图（预设视图和自定义视图）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/canvas/{canvasId}/views")
@RequiredArgsConstructor
public class CanvasViewController {

    private final CanvasViewService viewService;

    /**
     * 获取画布的所有视图
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<List<ViewResponse>> listViews(@PathVariable String canvasId) {
        List<ViewResponse> views = viewService.listViews(canvasId);
        return Result.success(views);
    }

    /**
     * 获取单个视图
     */
    @GetMapping("/{viewId}")
    @RequireWorkspaceMember
    public Result<ViewResponse> getView(
            @PathVariable String canvasId,
            @PathVariable String viewId) {
        ViewResponse view = viewService.getView(viewId);
        return Result.success(view);
    }

    /**
     * 获取默认视图
     */
    @GetMapping("/default")
    @RequireWorkspaceMember
    public Result<ViewResponse> getDefaultView(@PathVariable String canvasId) {
        ViewResponse view = viewService.getDefaultView(canvasId);
        return Result.success(view);
    }

    /**
     * 根据视图键获取视图
     */
    @GetMapping("/key/{viewKey}")
    @RequireWorkspaceMember
    public Result<ViewResponse> getViewByKey(
            @PathVariable String canvasId,
            @PathVariable String viewKey) {
        ViewResponse view = viewService.getViewByKey(canvasId, viewKey.toUpperCase());
        return Result.success(view);
    }

    /**
     * 创建自定义视图
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<ViewResponse> createView(
            @PathVariable String canvasId,
            @RequestBody @Valid CreateViewRequest request) {
        String userId = UserContextHolder.getUserId();
        request.setCanvasId(canvasId);
        ViewResponse view = viewService.createCustomView(request, userId);
        return Result.success(view);
    }

    /**
     * 更新视图
     */
    @PutMapping("/{viewId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<ViewResponse> updateView(
            @PathVariable String canvasId,
            @PathVariable String viewId,
            @RequestBody @Valid UpdateViewRequest request) {
        String userId = UserContextHolder.getUserId();
        ViewResponse view = viewService.updateView(viewId, request, userId);
        return Result.success(view);
    }

    /**
     * 删除自定义视图（预设视图不可删除）
     */
    @DeleteMapping("/{viewId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteView(
            @PathVariable String canvasId,
            @PathVariable String viewId) {
        String userId = UserContextHolder.getUserId();
        viewService.deleteView(viewId, userId);
        return Result.success();
    }

    /**
     * 设置默认视图
     */
    @PostMapping("/{viewId}/set-default")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setDefaultView(
            @PathVariable String canvasId,
            @PathVariable String viewId) {
        String userId = UserContextHolder.getUserId();
        viewService.setDefaultView(canvasId, viewId, userId);
        return Result.success();
    }
}
