package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.relation.*;
import com.actionow.project.service.EntityAssetRelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体-素材关联控制器
 * 管理实体（剧本、剧集、分镜、角色、场景、道具等）与素材之间的关联关系
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/entity-assets")
@RequiredArgsConstructor
public class EntityAssetRelationController {

    private final EntityAssetRelationService entityAssetRelationService;

    // ==================== 关联管理 ====================

    /**
     * 创建实体-素材关联
     */
    @PostMapping("/relations")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityAssetRelationResponse> createRelation(@RequestBody @Valid CreateEntityAssetRelationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        EntityAssetRelationResponse response = entityAssetRelationService.createRelation(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 批量创建实体-素材关联
     */
    @PostMapping("/relations/batch")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<List<EntityAssetRelationResponse>> batchCreateRelations(@RequestBody @Valid BatchCreateEntityAssetRelationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        List<EntityAssetRelationResponse> responses = entityAssetRelationService.batchCreateRelations(request, workspaceId, userId);
        return Result.success(responses);
    }

    /**
     * 删除关联（按关联ID）
     */
    @DeleteMapping("/relations/{relationId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteRelation(@PathVariable String relationId) {
        String userId = UserContextHolder.getUserId();
        entityAssetRelationService.deleteRelation(relationId, userId);
        return Result.success();
    }

    /**
     * 更新关联类型
     */
    @PutMapping("/relations/{relationId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityAssetRelationResponse> updateRelationType(
            @PathVariable String relationId,
            @RequestBody @Valid UpdateEntityAssetRelationRequest request) {
        String userId = UserContextHolder.getUserId();
        EntityAssetRelationResponse response = entityAssetRelationService.updateRelationType(
                relationId, request.getRelationType(), userId);
        return Result.success(response);
    }

    // ==================== 查询接口 ====================

    /**
     * 分页查询实体关联的素材（核心查询接口）
     * 支持按素材类型、关联类型、关键词过滤
     */
    @GetMapping("/{entityType}/{entityId}/query")
    @RequireWorkspaceMember
    public Result<Page<EntityAssetRelationResponse>> queryEntityAssets(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(required = false) List<String> assetTypes,
            @RequestParam(required = false) List<String> relationTypes,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        EntityAssetQueryRequest request = EntityAssetQueryRequest.builder()
                .entityType(entityType)
                .entityId(entityId)
                .assetTypes(assetTypes)
                .relationTypes(relationTypes)
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();
        Page<EntityAssetRelationResponse> result = entityAssetRelationService.queryEntityAssets(request, workspaceId);
        return Result.success(result);
    }

    /**
     * 查询实体关联的素材列表（不分页）
     */
    @GetMapping("/{entityType}/{entityId}")
    @RequireWorkspaceMember
    public Result<List<EntityAssetRelationResponse>> listEntityAssets(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<EntityAssetRelationResponse> assets = entityAssetRelationService.listEntityAssets(entityType, entityId, workspaceId);
        return Result.success(assets);
    }

    /**
     * 根据关联类型查询实体关联的素材
     */
    @GetMapping("/{entityType}/{entityId}/by-type/{relationType}")
    @RequireWorkspaceMember
    public Result<List<EntityAssetRelationResponse>> listByRelationType(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @PathVariable String relationType) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<EntityAssetRelationResponse> assets = entityAssetRelationService.listByRelationType(entityType, entityId, relationType, workspaceId);
        return Result.success(assets);
    }

    /**
     * 获取实体的素材关联汇总统计
     */
    @GetMapping("/{entityType}/{entityId}/summary")
    @RequireWorkspaceMember
    public Result<EntityAssetSummaryResponse> getEntityAssetSummary(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        EntityAssetSummaryResponse summary = entityAssetRelationService.getEntityAssetSummary(entityType, entityId, workspaceId);
        return Result.success(summary);
    }

    /**
     * 查询素材被哪些实体关联
     */
    @GetMapping("/asset/{assetId}/relations")
    @RequireWorkspaceMember
    public Result<List<EntityAssetRelationResponse>> listAssetRelations(@PathVariable String assetId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<EntityAssetRelationResponse> relations = entityAssetRelationService.listAssetRelations(assetId, workspaceId);
        return Result.success(relations);
    }

    // ==================== 便捷操作 ====================

    /**
     * 挂载素材到实体（语义化端点，等价于创建关联）
     */
    @PostMapping("/mount")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityAssetRelationResponse> mountAsset(@RequestBody @Valid MountAssetRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        EntityAssetRelationResponse response = entityAssetRelationService.mountAsset(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 从实体解挂素材（语义化端点，等价于删除关联）
     */
    @PostMapping("/unmount")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> unmountAsset(@RequestBody @Valid UnmountAssetRequest request) {
        String userId = UserContextHolder.getUserId();
        entityAssetRelationService.unmountAsset(request, userId);
        return Result.success();
    }

    /**
     * 设置素材为正式素材
     * 将指定素材设为正式素材，同时将该实体其他正式素材改为草稿
     */
    @PostMapping("/{entityType}/{entityId}/set-official/{assetId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setAsOfficial(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @PathVariable String assetId) {
        String userId = UserContextHolder.getUserId();
        entityAssetRelationService.setAsOfficial(entityType, entityId, assetId, userId);
        return Result.success();
    }
}
