package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.CreateSceneRequest;
import com.actionow.project.dto.UpdateSceneRequest;
import com.actionow.project.dto.SceneQueryRequest;
import com.actionow.project.dto.SceneDetailResponse;
import com.actionow.project.dto.SceneListResponse;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.SceneVersionDetailResponse;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.SceneService;
import com.actionow.project.service.version.SceneVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景控制器
 * workspaceId 从请求头获取（通过 UserContextHolder）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/scenes")
@RequiredArgsConstructor
public class SceneController {

    private final SceneService sceneService;
    private final SceneVersionServiceImpl sceneVersionService;

    /**
     * 创建场景
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<SceneDetailResponse> createScene(@RequestBody @Valid CreateSceneRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        SceneDetailResponse response = sceneService.create(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 更新场景
     */
    @PutMapping("/{sceneId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<SceneDetailResponse> updateScene(
            @PathVariable String sceneId,
            @RequestBody @Valid UpdateSceneRequest request) {
        String userId = UserContextHolder.getUserId();
        SceneDetailResponse response = sceneService.update(sceneId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除场景
     */
    @DeleteMapping("/{sceneId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteScene(@PathVariable String sceneId) {
        String userId = UserContextHolder.getUserId();
        sceneService.delete(sceneId, userId);
        return Result.success();
    }

    /**
     * 获取工作空间级场景列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<List<SceneListResponse>> listWorkspaceScenes() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<SceneListResponse> scenes = sceneService.listWorkspaceScenes(workspaceId);
        return Result.success(scenes);
    }

    /**
     * 分页查询场景（支持搜索和过滤）
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<SceneListResponse>> queryScenes(SceneQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<SceneListResponse> page = sceneService.queryScenes(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 获取剧本级场景列表
     */
    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<SceneListResponse>> listScriptScenes(@PathVariable String scriptId) {
        List<SceneListResponse> scenes = sceneService.listScriptScenes(scriptId);
        return Result.success(scenes);
    }

    /**
     * 获取剧本可用的所有场景
     * 支持模糊搜索和数量限制
     *
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制，默认50
     */
    @GetMapping("/available/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<SceneListResponse>> listAvailableScenes(
            @PathVariable String scriptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<SceneListResponse> scenes = sceneService.listAvailableScenes(workspaceId, scriptId, keyword, limit);
        return Result.success(scenes);
    }

    /**
     * 获取场景详情
     */
    @GetMapping("/{sceneId}")
    @RequireWorkspaceMember
    public Result<SceneDetailResponse> getScene(@PathVariable String sceneId) {
        SceneDetailResponse response = sceneService.getById(sceneId);
        return Result.success(response);
    }

    /**
     * 设置场景封面
     */
    @PutMapping("/{sceneId}/cover")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setCover(
            @PathVariable String sceneId,
            @RequestParam String assetId) {
        String userId = UserContextHolder.getUserId();
        sceneService.setCover(sceneId, assetId, userId);
        return Result.success();
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取场景的版本列表
     */
    @GetMapping("/{sceneId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String sceneId) {
        List<VersionInfoResponse> versions = sceneVersionService.listVersions(sceneId);
        return Result.success(versions);
    }

    /**
     * 获取场景的指定版本详情
     */
    @GetMapping("/{sceneId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<SceneVersionDetailResponse> getVersion(
            @PathVariable String sceneId,
            @PathVariable Integer versionNumber) {
        SceneVersionDetailResponse version = sceneVersionService.getVersion(sceneId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取场景的当前版本
     */
    @GetMapping("/{sceneId}/versions/current")
    @RequireWorkspaceMember
    public Result<SceneVersionDetailResponse> getCurrentVersion(@PathVariable String sceneId) {
        SceneVersionDetailResponse version = sceneVersionService.getCurrentVersion(sceneId);
        return Result.success(version);
    }

    /**
     * 恢复场景到指定版本
     */
    @PostMapping("/{sceneId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String sceneId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = sceneVersionService.restoreVersion(sceneId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{sceneId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String sceneId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = sceneVersionService.compareVersions(sceneId, version1, version2);
        return Result.success(diff);
    }
}
