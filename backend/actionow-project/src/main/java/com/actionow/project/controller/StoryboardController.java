package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.*;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.StoryboardVersionDetailResponse;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.StoryboardService;
import com.actionow.project.service.version.StoryboardVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分镜控制器
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/storyboards")
@RequiredArgsConstructor
public class StoryboardController {

    private final StoryboardService storyboardService;
    private final StoryboardVersionServiceImpl storyboardVersionService;

    /**
     * 创建分镜
     * 支持两种方式传递 episodeId：
     * 1. 通过 RequestParam（向后兼容）
     * 2. 通过 RequestBody 的 episodeId 字段（推荐）
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<StoryboardDetailResponse> createStoryboard(
            @RequestParam(required = false) String episodeId,
            @RequestBody @Valid CreateStoryboardRequest request) {
        // 优先使用 RequestBody 中的 episodeId
        String effectiveEpisodeId = request.getEpisodeId() != null ? request.getEpisodeId() : episodeId;
        if (effectiveEpisodeId == null) {
            return Result.fail("episodeId 不能为空");
        }
        String userId = UserContextHolder.getUserId();
        StoryboardDetailResponse response = storyboardService.create(effectiveEpisodeId, request, userId);
        return Result.success(response);
    }

    /**
     * 获取分镜详情
     */
    @GetMapping("/{storyboardId}")
    @RequireWorkspaceMember
    public Result<StoryboardDetailResponse> getStoryboard(@PathVariable String storyboardId) {
        StoryboardDetailResponse response = storyboardService.getById(storyboardId);
        return Result.success(response);
    }

    /**
     * 更新分镜
     */
    @PutMapping("/{storyboardId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<StoryboardDetailResponse> updateStoryboard(
            @PathVariable String storyboardId,
            @RequestBody @Valid UpdateStoryboardRequest request) {
        String userId = UserContextHolder.getUserId();
        StoryboardDetailResponse response = storyboardService.update(storyboardId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除分镜
     */
    @DeleteMapping("/{storyboardId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteStoryboard(@PathVariable String storyboardId) {
        String userId = UserContextHolder.getUserId();
        storyboardService.delete(storyboardId, userId);
        return Result.success();
    }

    /**
     * 获取剧集的分镜列表
     * 支持模糊搜索和数量限制
     *
     * @param episodeId 剧集ID
     * @param keyword 搜索关键词（匹配标题、描述）
     * @param limit 返回数量限制，默认50
     */
    @GetMapping("/episode/{episodeId}")
    @RequireWorkspaceMember
    public Result<List<StoryboardListResponse>> listStoryboardsByEpisode(
            @PathVariable String episodeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        List<StoryboardListResponse> storyboards = storyboardService.listByEpisode(episodeId, keyword, limit);
        return Result.success(storyboards);
    }

    /**
     * 获取剧本的所有分镜
     */
    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<StoryboardListResponse>> listStoryboardsByScript(@PathVariable String scriptId) {
        List<StoryboardListResponse> storyboards = storyboardService.listByScript(scriptId);
        return Result.success(storyboards);
    }

    /**
     * 分页查询分镜（支持搜索和过滤）
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<StoryboardListResponse>> queryStoryboards(StoryboardQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<StoryboardListResponse> page = storyboardService.queryStoryboards(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 调整分镜顺序
     */
    @PutMapping("/reorder")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> reorderStoryboards(
            @RequestParam String episodeId,
            @RequestBody List<String> storyboardIds) {
        String userId = UserContextHolder.getUserId();
        storyboardService.reorder(episodeId, storyboardIds, userId);
        return Result.success();
    }

    /**
     * 设置分镜封面
     */
    @PutMapping("/{storyboardId}/cover")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setCover(
            @PathVariable String storyboardId,
            @RequestParam String assetId) {
        String userId = UserContextHolder.getUserId();
        storyboardService.setCover(storyboardId, assetId, userId);
        return Result.success();
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取分镜的版本列表
     */
    @GetMapping("/{storyboardId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String storyboardId) {
        List<VersionInfoResponse> versions = storyboardVersionService.listVersions(storyboardId);
        return Result.success(versions);
    }

    /**
     * 获取分镜的指定版本详情
     */
    @GetMapping("/{storyboardId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<StoryboardVersionDetailResponse> getVersion(
            @PathVariable String storyboardId,
            @PathVariable Integer versionNumber) {
        StoryboardVersionDetailResponse version = storyboardVersionService.getVersion(storyboardId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取分镜的当前版本
     */
    @GetMapping("/{storyboardId}/versions/current")
    @RequireWorkspaceMember
    public Result<StoryboardVersionDetailResponse> getCurrentVersion(@PathVariable String storyboardId) {
        StoryboardVersionDetailResponse version = storyboardVersionService.getCurrentVersion(storyboardId);
        return Result.success(version);
    }

    /**
     * 恢复分镜到指定版本
     */
    @PostMapping("/{storyboardId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String storyboardId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = storyboardVersionService.restoreVersion(storyboardId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{storyboardId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String storyboardId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = storyboardVersionService.compareVersions(storyboardId, version1, version2);
        return Result.success(diff);
    }
}
