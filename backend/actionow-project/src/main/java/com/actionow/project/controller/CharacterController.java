package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.CharacterQueryRequest;
import com.actionow.project.dto.CharacterDetailResponse;
import com.actionow.project.dto.CharacterListResponse;
import com.actionow.project.dto.CreateCharacterRequest;
import com.actionow.project.dto.UpdateCharacterRequest;
import com.actionow.project.dto.version.CharacterVersionDetailResponse;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.CharacterService;
import com.actionow.project.service.version.CharacterVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色控制器
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;
    private final CharacterVersionServiceImpl characterVersionService;

    /**
     * 创建角色
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<CharacterDetailResponse> createCharacter(@RequestBody @Valid CreateCharacterRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        CharacterDetailResponse response = characterService.create(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 更新角色
     */
    @PutMapping("/{characterId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<CharacterDetailResponse> updateCharacter(
            @PathVariable String characterId,
            @RequestBody @Valid UpdateCharacterRequest request) {
        String userId = UserContextHolder.getUserId();
        CharacterDetailResponse response = characterService.update(characterId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{characterId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteCharacter(@PathVariable String characterId) {
        String userId = UserContextHolder.getUserId();
        characterService.delete(characterId, userId);
        return Result.success();
    }

    /**
     * 获取工作空间级角色列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<List<CharacterListResponse>> listWorkspaceCharacters() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<CharacterListResponse> characters = characterService.listWorkspaceCharacters(workspaceId);
        return Result.success(characters);
    }

    /**
     * 分页查询角色（支持搜索和过滤）
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<CharacterListResponse>> queryCharacters(CharacterQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<CharacterListResponse> page = characterService.queryCharacters(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 获取剧本级角色列表
     */
    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<CharacterListResponse>> listScriptCharacters(@PathVariable String scriptId) {
        List<CharacterListResponse> characters = characterService.listScriptCharacters(scriptId);
        return Result.success(characters);
    }

    /**
     * 获取剧本可用的所有角色（工作空间级 + 剧本级）
     * 支持模糊搜索和数量限制
     *
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制，默认50
     */
    @GetMapping("/available/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<CharacterListResponse>> listAvailableCharacters(
            @PathVariable String scriptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<CharacterListResponse> characters = characterService.listAvailableCharacters(workspaceId, scriptId, keyword, limit);
        return Result.success(characters);
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/{characterId}")
    @RequireWorkspaceMember
    public Result<CharacterDetailResponse> getCharacter(@PathVariable String characterId) {
        CharacterDetailResponse response = characterService.getById(characterId);
        return Result.success(response);
    }

    /**
     * 设置角色封面
     */
    @PutMapping("/{characterId}/cover")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setCover(
            @PathVariable String characterId,
            @RequestParam String assetId) {
        String userId = UserContextHolder.getUserId();
        characterService.setCover(characterId, assetId, userId);
        return Result.success();
    }

    /**
     * 设置语音种子
     */
    @PutMapping("/{characterId}/voice-seed")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setVoiceSeed(
            @PathVariable String characterId,
            @RequestParam String voiceSeedId) {
        String userId = UserContextHolder.getUserId();
        characterService.setVoiceSeed(characterId, voiceSeedId, userId);
        return Result.success();
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取角色的版本列表
     */
    @GetMapping("/{characterId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String characterId) {
        List<VersionInfoResponse> versions = characterVersionService.listVersions(characterId);
        return Result.success(versions);
    }

    /**
     * 获取角色的指定版本详情
     */
    @GetMapping("/{characterId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<CharacterVersionDetailResponse> getVersion(
            @PathVariable String characterId,
            @PathVariable Integer versionNumber) {
        CharacterVersionDetailResponse version = characterVersionService.getVersion(characterId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取角色的当前版本
     */
    @GetMapping("/{characterId}/versions/current")
    @RequireWorkspaceMember
    public Result<CharacterVersionDetailResponse> getCurrentVersion(@PathVariable String characterId) {
        CharacterVersionDetailResponse version = characterVersionService.getCurrentVersion(characterId);
        return Result.success(version);
    }

    /**
     * 恢复角色到指定版本
     */
    @PostMapping("/{characterId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String characterId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = characterVersionService.restoreVersion(characterId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{characterId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String characterId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = characterVersionService.compareVersions(characterId, version1, version2);
        return Result.success(diff);
    }
}
