package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.*;
import com.actionow.project.dto.version.EpisodeVersionDetailResponse;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.EpisodeService;
import com.actionow.project.service.version.EpisodeVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 剧集控制器
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/episodes")
@RequiredArgsConstructor
public class EpisodeController {

    private final EpisodeService episodeService;
    private final EpisodeVersionServiceImpl episodeVersionService;

    /**
     * 创建剧集
     * 支持两种方式传递 scriptId：
     * 1. 通过 RequestParam（向后兼容）
     * 2. 通过 RequestBody 的 scriptId 字段（推荐）
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EpisodeDetailResponse> createEpisode(
            @RequestParam(required = false) String scriptId,
            @RequestBody @Valid CreateEpisodeRequest request) {
        // 优先使用 RequestBody 中的 scriptId
        String effectiveScriptId = request.getScriptId() != null ? request.getScriptId() : scriptId;
        if (effectiveScriptId == null) {
            return Result.fail("scriptId 不能为空");
        }
        String userId = UserContextHolder.getUserId();
        EpisodeDetailResponse response = episodeService.create(effectiveScriptId, request, userId);
        return Result.success(response);
    }

    /**
     * 获取剧集详情
     */
    @GetMapping("/{episodeId}")
    @RequireWorkspaceMember
    public Result<EpisodeDetailResponse> getEpisode(@PathVariable String episodeId) {
        EpisodeDetailResponse response = episodeService.getById(episodeId);
        return Result.success(response);
    }

    /**
     * 更新剧集
     */
    @PutMapping("/{episodeId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<EpisodeDetailResponse> updateEpisode(
            @PathVariable String episodeId,
            @RequestBody @Valid UpdateEpisodeRequest request) {
        String userId = UserContextHolder.getUserId();
        EpisodeDetailResponse response = episodeService.update(episodeId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除剧集
     */
    @DeleteMapping("/{episodeId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteEpisode(@PathVariable String episodeId) {
        String userId = UserContextHolder.getUserId();
        episodeService.delete(episodeId, userId);
        return Result.success();
    }

    /**
     * 获取剧本的剧集列表
     * 支持模糊搜索和数量限制
     *
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配标题、简介）
     * @param limit 返回数量限制，默认50
     */
    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<EpisodeListResponse>> listEpisodesByScript(
            @PathVariable String scriptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        List<EpisodeListResponse> episodes = episodeService.listByScript(scriptId, keyword, limit);
        return Result.success(episodes);
    }

    /**
     * 分页查询剧集（支持搜索和过滤）
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<EpisodeListResponse>> queryEpisodes(EpisodeQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<EpisodeListResponse> page = episodeService.queryEpisodes(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 调整剧集顺序
     */
    @PutMapping("/reorder")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> reorderEpisodes(
            @RequestParam String scriptId,
            @RequestBody List<String> episodeIds) {
        String userId = UserContextHolder.getUserId();
        episodeService.reorder(scriptId, episodeIds, userId);
        return Result.success();
    }

    /**
     * 设置剧集封面
     */
    @PutMapping("/{episodeId}/cover")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setCover(
            @PathVariable String episodeId,
            @RequestParam String assetId) {
        String userId = UserContextHolder.getUserId();
        episodeService.setCover(episodeId, assetId, userId);
        return Result.success();
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取剧集的版本列表
     */
    @GetMapping("/{episodeId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String episodeId) {
        List<VersionInfoResponse> versions = episodeVersionService.listVersions(episodeId);
        return Result.success(versions);
    }

    /**
     * 获取剧集的指定版本详情
     */
    @GetMapping("/{episodeId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<EpisodeVersionDetailResponse> getVersion(
            @PathVariable String episodeId,
            @PathVariable Integer versionNumber) {
        EpisodeVersionDetailResponse version = episodeVersionService.getVersion(episodeId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取剧集的当前版本
     */
    @GetMapping("/{episodeId}/versions/current")
    @RequireWorkspaceMember
    public Result<EpisodeVersionDetailResponse> getCurrentVersion(@PathVariable String episodeId) {
        EpisodeVersionDetailResponse version = episodeVersionService.getCurrentVersion(episodeId);
        return Result.success(version);
    }

    /**
     * 恢复剧集到指定版本
     */
    @PostMapping("/{episodeId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String episodeId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = episodeVersionService.restoreVersion(episodeId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{episodeId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String episodeId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = episodeVersionService.compareVersions(episodeId, version1, version2);
        return Result.success(diff);
    }
}
