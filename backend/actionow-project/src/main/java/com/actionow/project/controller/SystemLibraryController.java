package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.project.dto.library.*;
import com.actionow.project.service.SystemLibraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统管理员资源库控制器
 * 所有接口均需要系统租户 Admin+ 角色（@RequireSystemTenant(minRole="Admin")）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/system/library")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class SystemLibraryController {

    private final SystemLibraryService systemLibraryService;

    // ==================== 角色管理 ====================

    /**
     * 查询系统租户所有角色（含草稿 scope=WORKSPACE 和已发布 scope=SYSTEM）
     */
    @GetMapping("/characters")
    public Result<Page<SystemCharacterResponse>> listCharacters(SystemLibraryQueryRequest request) {
        return Result.success(systemLibraryService.listSystemCharacters(request));
    }

    /**
     * 发布角色到公共库（WORKSPACE → SYSTEM）
     */
    @PatchMapping("/characters/{id}/publish")
    public Result<Void> publishCharacter(
            @PathVariable String id,
            @RequestBody(required = false) @Valid PublishResourceRequest request) {
        String operatorId = UserContextHolder.getUserId();
        systemLibraryService.publishCharacter(id, request, operatorId);
        return Result.success();
    }

    /**
     * 下架角色（SYSTEM → WORKSPACE）
     */
    @PatchMapping("/characters/{id}/unpublish")
    public Result<Void> unpublishCharacter(@PathVariable String id) {
        String operatorId = UserContextHolder.getUserId();
        systemLibraryService.unpublishCharacter(id, operatorId);
        return Result.success();
    }

    // ==================== 场景管理 ====================

    @GetMapping("/scenes")
    public Result<Page<SystemSceneResponse>> listScenes(SystemLibraryQueryRequest request) {
        return Result.success(systemLibraryService.listSystemScenes(request));
    }

    @PatchMapping("/scenes/{id}/publish")
    public Result<Void> publishScene(
            @PathVariable String id,
            @RequestBody(required = false) @Valid PublishResourceRequest request) {
        systemLibraryService.publishScene(id, request, UserContextHolder.getUserId());
        return Result.success();
    }

    @PatchMapping("/scenes/{id}/unpublish")
    public Result<Void> unpublishScene(@PathVariable String id) {
        systemLibraryService.unpublishScene(id, UserContextHolder.getUserId());
        return Result.success();
    }

    // ==================== 道具管理 ====================

    @GetMapping("/props")
    public Result<Page<SystemPropResponse>> listProps(SystemLibraryQueryRequest request) {
        return Result.success(systemLibraryService.listSystemProps(request));
    }

    @PatchMapping("/props/{id}/publish")
    public Result<Void> publishProp(
            @PathVariable String id,
            @RequestBody(required = false) @Valid PublishResourceRequest request) {
        systemLibraryService.publishProp(id, request, UserContextHolder.getUserId());
        return Result.success();
    }

    @PatchMapping("/props/{id}/unpublish")
    public Result<Void> unpublishProp(@PathVariable String id) {
        systemLibraryService.unpublishProp(id, UserContextHolder.getUserId());
        return Result.success();
    }

    // ==================== 风格管理 ====================

    @GetMapping("/styles")
    public Result<Page<SystemStyleResponse>> listStyles(SystemLibraryQueryRequest request) {
        return Result.success(systemLibraryService.listSystemStyles(request));
    }

    @PatchMapping("/styles/{id}/publish")
    public Result<Void> publishStyle(
            @PathVariable String id,
            @RequestBody(required = false) @Valid PublishResourceRequest request) {
        systemLibraryService.publishStyle(id, request, UserContextHolder.getUserId());
        return Result.success();
    }

    @PatchMapping("/styles/{id}/unpublish")
    public Result<Void> unpublishStyle(@PathVariable String id) {
        systemLibraryService.unpublishStyle(id, UserContextHolder.getUserId());
        return Result.success();
    }

    // ==================== 素材管理 ====================

    @GetMapping("/assets")
    public Result<Page<SystemAssetResponse>> listAssets(SystemLibraryQueryRequest request) {
        return Result.success(systemLibraryService.listSystemAssets(request));
    }

    @PatchMapping("/assets/{id}/publish")
    public Result<Void> publishAsset(
            @PathVariable String id,
            @RequestBody(required = false) @Valid PublishResourceRequest request) {
        systemLibraryService.publishAsset(id, request, UserContextHolder.getUserId());
        return Result.success();
    }

    @PatchMapping("/assets/{id}/unpublish")
    public Result<Void> unpublishAsset(@PathVariable String id) {
        systemLibraryService.unpublishAsset(id, UserContextHolder.getUserId());
        return Result.success();
    }
}
