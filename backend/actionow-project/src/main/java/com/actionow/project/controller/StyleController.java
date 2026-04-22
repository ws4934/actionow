package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.CreateStyleRequest;
import com.actionow.project.dto.UpdateStyleRequest;
import com.actionow.project.dto.StyleQueryRequest;
import com.actionow.project.dto.StyleDetailResponse;
import com.actionow.project.dto.StyleListResponse;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.StyleVersionDetailResponse;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.StyleService;
import com.actionow.project.service.version.StyleVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 风格控制器
 * workspaceId 从请求头获取（通过 UserContextHolder）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/styles")
@RequiredArgsConstructor
public class StyleController {

    private final StyleService styleService;
    private final StyleVersionServiceImpl styleVersionService;

    /**
     * 创建风格
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<StyleDetailResponse> createStyle(@RequestBody @Valid CreateStyleRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        StyleDetailResponse response = styleService.create(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 更新风格
     */
    @PutMapping("/{styleId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<StyleDetailResponse> updateStyle(
            @PathVariable String styleId,
            @RequestBody @Valid UpdateStyleRequest request) {
        String userId = UserContextHolder.getUserId();
        StyleDetailResponse response = styleService.update(styleId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除风格
     */
    @DeleteMapping("/{styleId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteStyle(@PathVariable String styleId) {
        String userId = UserContextHolder.getUserId();
        styleService.delete(styleId, userId);
        return Result.success();
    }

    /**
     * 获取工作空间级风格列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<List<StyleListResponse>> listWorkspaceStyles() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<StyleListResponse> styles = styleService.listWorkspaceStyles(workspaceId);
        return Result.success(styles);
    }

    /**
     * 分页查询风格（支持搜索和过滤）
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<StyleListResponse>> queryStyles(StyleQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<StyleListResponse> page = styleService.queryStyles(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 获取剧本级风格列表
     */
    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<StyleListResponse>> listScriptStyles(@PathVariable String scriptId) {
        List<StyleListResponse> styles = styleService.listScriptStyles(scriptId);
        return Result.success(styles);
    }

    /**
     * 获取剧本可用的所有风格
     * 支持模糊搜索和数量限制
     *
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制，默认50
     */
    @GetMapping("/available/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<StyleListResponse>> listAvailableStyles(
            @PathVariable String scriptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<StyleListResponse> styles = styleService.listAvailableStyles(workspaceId, scriptId, keyword, limit);
        return Result.success(styles);
    }

    /**
     * 获取风格详情
     */
    @GetMapping("/{styleId}")
    @RequireWorkspaceMember
    public Result<StyleDetailResponse> getStyle(@PathVariable String styleId) {
        StyleDetailResponse response = styleService.getById(styleId);
        return Result.success(response);
    }

    /**
     * 设置风格封面
     */
    @PutMapping("/{styleId}/cover")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setCover(
            @PathVariable String styleId,
            @RequestParam String assetId) {
        String userId = UserContextHolder.getUserId();
        styleService.setCover(styleId, assetId, userId);
        return Result.success();
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取风格的版本列表
     */
    @GetMapping("/{styleId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String styleId) {
        List<VersionInfoResponse> versions = styleVersionService.listVersions(styleId);
        return Result.success(versions);
    }

    /**
     * 获取风格的指定版本详情
     */
    @GetMapping("/{styleId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<StyleVersionDetailResponse> getVersion(
            @PathVariable String styleId,
            @PathVariable Integer versionNumber) {
        StyleVersionDetailResponse version = styleVersionService.getVersion(styleId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取风格的当前版本
     */
    @GetMapping("/{styleId}/versions/current")
    @RequireWorkspaceMember
    public Result<StyleVersionDetailResponse> getCurrentVersion(@PathVariable String styleId) {
        StyleVersionDetailResponse version = styleVersionService.getCurrentVersion(styleId);
        return Result.success(version);
    }

    /**
     * 恢复风格到指定版本
     */
    @PostMapping("/{styleId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String styleId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = styleVersionService.restoreVersion(styleId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{styleId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String styleId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = styleVersionService.compareVersions(styleId, version1, version2);
        return Result.success(diff);
    }
}
