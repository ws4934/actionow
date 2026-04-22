package com.actionow.project.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.relation.*;
import com.actionow.project.service.EntityRelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实体关系控制器
 * 管理实体之间的关联关系，如分镜与角色、场景、道具、对白的关系
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/entity-relations")
@RequiredArgsConstructor
public class EntityRelationController {

    private final EntityRelationService entityRelationService;

    // ==================== 关系管理 ====================

    /**
     * 创建实体关系
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityRelationResponse> createRelation(@RequestBody @Valid CreateEntityRelationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        EntityRelationResponse response = entityRelationService.createRelation(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 批量创建实体关系
     */
    @PostMapping("/batch")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<List<EntityRelationResponse>> batchCreateRelations(@RequestBody @Valid List<CreateEntityRelationRequest> requests) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        List<EntityRelationResponse> responses = entityRelationService.batchCreateRelations(requests, workspaceId, userId);
        return Result.success(responses);
    }

    /**
     * 更新关系
     */
    @PutMapping("/{relationId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityRelationResponse> updateRelation(
            @PathVariable String relationId,
            @RequestBody @Valid UpdateEntityRelationRequest request) {
        String userId = UserContextHolder.getUserId();
        EntityRelationResponse response = entityRelationService.updateRelation(relationId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除关系（按ID）
     */
    @DeleteMapping("/{relationId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteRelation(@PathVariable String relationId) {
        String userId = UserContextHolder.getUserId();
        entityRelationService.deleteRelation(relationId, userId);
        return Result.success();
    }

    /**
     * 删除源实体的所有关系
     */
    @DeleteMapping("/source/{sourceType}/{sourceId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteBySource(
            @PathVariable String sourceType,
            @PathVariable String sourceId) {
        String userId = UserContextHolder.getUserId();
        entityRelationService.deleteBySource(sourceType, sourceId, userId);
        return Result.success();
    }

    /**
     * 删除源实体指定类型的关系
     */
    @DeleteMapping("/source/{sourceType}/{sourceId}/type/{relationType}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteBySourceAndRelationType(
            @PathVariable String sourceType,
            @PathVariable String sourceId,
            @PathVariable String relationType) {
        String userId = UserContextHolder.getUserId();
        entityRelationService.deleteBySourceAndRelationType(sourceType, sourceId, relationType, userId);
        return Result.success();
    }

    // ==================== 查询接口 ====================

    /**
     * 根据ID获取关系
     */
    @GetMapping("/{relationId}")
    @RequireWorkspaceMember
    public Result<EntityRelationResponse> getById(@PathVariable String relationId) {
        EntityRelationResponse response = entityRelationService.getById(relationId);
        return Result.success(response);
    }

    /**
     * 查询源实体的所有关系
     */
    @GetMapping("/source/{sourceType}/{sourceId}")
    @RequireWorkspaceMember
    public Result<List<EntityRelationResponse>> listBySource(
            @PathVariable String sourceType,
            @PathVariable String sourceId) {
        List<EntityRelationResponse> relations = entityRelationService.listBySource(sourceType, sourceId);
        return Result.success(relations);
    }

    /**
     * 查询源实体指定类型的关系
     */
    @GetMapping("/source/{sourceType}/{sourceId}/type/{relationType}")
    @RequireWorkspaceMember
    public Result<List<EntityRelationResponse>> listBySourceAndRelationType(
            @PathVariable String sourceType,
            @PathVariable String sourceId,
            @PathVariable String relationType) {
        List<EntityRelationResponse> relations = entityRelationService.listBySourceAndRelationType(sourceType, sourceId, relationType);
        return Result.success(relations);
    }

    /**
     * 查询源实体指定目标类型的关系
     */
    @GetMapping("/source/{sourceType}/{sourceId}/target-type/{targetType}")
    @RequireWorkspaceMember
    public Result<List<EntityRelationResponse>> listBySourceAndTargetType(
            @PathVariable String sourceType,
            @PathVariable String sourceId,
            @PathVariable String targetType) {
        List<EntityRelationResponse> relations = entityRelationService.listBySourceAndTargetType(sourceType, sourceId, targetType);
        return Result.success(relations);
    }

    /**
     * 查询目标实体的所有入向关系
     */
    @GetMapping("/target/{targetType}/{targetId}")
    @RequireWorkspaceMember
    public Result<List<EntityRelationResponse>> listByTarget(
            @PathVariable String targetType,
            @PathVariable String targetId) {
        List<EntityRelationResponse> relations = entityRelationService.listByTarget(targetType, targetId);
        return Result.success(relations);
    }

    /**
     * 查询两个实体之间的关系
     */
    @GetMapping("/between")
    @RequireWorkspaceMember
    public Result<List<EntityRelationResponse>> listBetweenEntities(
            @RequestParam String sourceType,
            @RequestParam String sourceId,
            @RequestParam String targetType,
            @RequestParam String targetId) {
        List<EntityRelationResponse> relations = entityRelationService.listBetweenEntities(sourceType, sourceId, targetType, targetId);
        return Result.success(relations);
    }

    /**
     * 批量查询多个源实体的关系
     */
    @GetMapping("/batch/source/{sourceType}")
    @RequireWorkspaceMember
    public Result<Map<String, List<EntityRelationResponse>>> batchListBySource(
            @PathVariable String sourceType,
            @RequestParam List<String> sourceIds) {
        Map<String, List<EntityRelationResponse>> relationsMap = entityRelationService.batchListBySource(sourceType, sourceIds);
        return Result.success(relationsMap);
    }

    // ==================== 分镜关系便捷接口 ====================

    /**
     * 获取分镜的场景关系
     */
    @GetMapping("/storyboard/{storyboardId}/scene")
    @RequireWorkspaceMember
    public Result<StoryboardSceneRelation> getStoryboardScene(@PathVariable String storyboardId) {
        StoryboardSceneRelation scene = entityRelationService.getStoryboardScene(storyboardId);
        return Result.success(scene);
    }

    /**
     * 获取分镜的角色出现关系列表
     */
    @GetMapping("/storyboard/{storyboardId}/characters")
    @RequireWorkspaceMember
    public Result<List<StoryboardCharacterRelation>> listStoryboardCharacters(@PathVariable String storyboardId) {
        List<StoryboardCharacterRelation> characters = entityRelationService.listStoryboardCharacters(storyboardId);
        return Result.success(characters);
    }

    /**
     * 获取分镜的道具使用关系列表
     */
    @GetMapping("/storyboard/{storyboardId}/props")
    @RequireWorkspaceMember
    public Result<List<StoryboardPropRelation>> listStoryboardProps(@PathVariable String storyboardId) {
        List<StoryboardPropRelation> props = entityRelationService.listStoryboardProps(storyboardId);
        return Result.success(props);
    }

    /**
     * 获取分镜的对白关系列表
     */
    @GetMapping("/storyboard/{storyboardId}/dialogues")
    @RequireWorkspaceMember
    public Result<List<StoryboardDialogueRelation>> listStoryboardDialogues(@PathVariable String storyboardId) {
        List<StoryboardDialogueRelation> dialogues = entityRelationService.listStoryboardDialogues(storyboardId);
        return Result.success(dialogues);
    }

    /**
     * 获取分镜的所有关系（聚合）
     */
    @GetMapping("/storyboard/{storyboardId}/all")
    @RequireWorkspaceMember
    public Result<StoryboardRelationsResponse> getStoryboardRelations(@PathVariable String storyboardId) {
        StoryboardRelationsResponse relations = entityRelationService.getStoryboardRelations(storyboardId);
        return Result.success(relations);
    }

    /**
     * 批量获取多个分镜的关系
     */
    @GetMapping("/storyboard/batch")
    @RequireWorkspaceMember
    public Result<Map<String, StoryboardRelationsResponse>> batchGetStoryboardRelations(
            @RequestParam List<String> storyboardIds) {
        Map<String, StoryboardRelationsResponse> relationsMap = entityRelationService.batchGetStoryboardRelations(storyboardIds);
        return Result.success(relationsMap);
    }

    // ==================== 统计与检查接口 ====================

    /**
     * 统计源实体的关系数量
     */
    @GetMapping("/count/source/{sourceType}/{sourceId}")
    @RequireWorkspaceMember
    public Result<Integer> countBySource(
            @PathVariable String sourceType,
            @PathVariable String sourceId) {
        int count = entityRelationService.countBySource(sourceType, sourceId);
        return Result.success(count);
    }

    /**
     * 统计源实体指定类型的关系数量
     */
    @GetMapping("/count/source/{sourceType}/{sourceId}/type/{relationType}")
    @RequireWorkspaceMember
    public Result<Integer> countBySourceAndRelationType(
            @PathVariable String sourceType,
            @PathVariable String sourceId,
            @PathVariable String relationType) {
        int count = entityRelationService.countBySourceAndRelationType(sourceType, sourceId, relationType);
        return Result.success(count);
    }

    /**
     * 检查关系是否存在
     */
    @GetMapping("/exists")
    @RequireWorkspaceMember
    public Result<Boolean> existsRelation(
            @RequestParam String sourceType,
            @RequestParam String sourceId,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam String relationType) {
        boolean exists = entityRelationService.existsRelation(sourceType, sourceId, targetType, targetId, relationType);
        return Result.success(exists);
    }

    // ==================== 便捷操作 ====================

    /**
     * 获取或创建关系
     */
    @PostMapping("/get-or-create")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EntityRelationResponse> getOrCreate(@RequestBody @Valid CreateEntityRelationRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        EntityRelationResponse response = entityRelationService.getOrCreate(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 替换关系
     * 删除源实体指定类型的所有现有关系，然后创建新关系
     */
    @PostMapping("/replace/{sourceType}/{sourceId}/type/{relationType}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<List<EntityRelationResponse>> replaceRelations(
            @PathVariable String sourceType,
            @PathVariable String sourceId,
            @PathVariable String relationType,
            @RequestBody @Valid List<CreateEntityRelationRequest> requests) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        List<EntityRelationResponse> responses = entityRelationService.replaceRelations(
                sourceType, sourceId, relationType, requests, workspaceId, userId);
        return Result.success(responses);
    }
}
