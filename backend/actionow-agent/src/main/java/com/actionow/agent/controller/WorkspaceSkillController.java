package com.actionow.agent.controller;

import com.actionow.agent.dto.request.SkillCreateRequest;
import com.actionow.agent.dto.request.SkillUpdateRequest;
import com.actionow.agent.dto.response.SkillImportResult;
import com.actionow.agent.dto.response.SkillResponse;
import com.actionow.agent.service.AgentSkillService;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.service.ToolCatalogService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 工作空间级 Skill 管理控制器
 * 工作空间成员可查看，Admin+ 可管理
 *
 * @author Actionow
 */
@Slf4j
@Tag(name = "Workspace Skills", description = "工作空间级 Skill 管理")
@RestController
@RequestMapping("/agent/skills")
@RequiredArgsConstructor
@RequireWorkspaceMember
public class WorkspaceSkillController {

    private final AgentSkillService skillService;
    private final ToolCatalogService toolCatalogService;

    @Operation(summary = "列出当前工作空间可用的 Skill（SYSTEM + WORKSPACE），省略 content")
    @GetMapping
    public Result<PageResult<SkillResponse>> listWorkspaceSkills(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "关键词（name/description 模糊匹配）") @RequestParam(required = false) String keyword) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(skillService.findPageForWorkspace(page, size, keyword, workspaceId));
    }

    @Operation(summary = "获取 Skill 详情（含 content）")
    @GetMapping("/{name}")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<SkillResponse> getWorkspaceSkill(@PathVariable String name) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(skillService.getByNameForWorkspace(name, workspaceId));
    }

    @Operation(summary = "获取 Skill 的 outputSchema（JSON Schema）",
            description = "仅返回 outputSchema 字段，供前端基于 structured_data SSE 事件做表单/卡片渲染。"
                    + "返回 null 表示该 Skill 未配置 outputSchema（不强制结构化输出）。")
    @GetMapping("/{name}/output-schema")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<java.util.Map<String, Object>> getWorkspaceSkillOutputSchema(@PathVariable String name) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        SkillResponse skill = skillService.getByNameForWorkspace(name, workspaceId);
        return Result.success(skill != null ? skill.getOutputSchema() : null);
    }

    @Operation(summary = "获取 Skill 绑定的工具详情列表")
    @GetMapping("/{name}/tools")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<List<ToolInfo>> getWorkspaceSkillTools(@PathVariable String name) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(toolCatalogService.getToolsForSkill(name, workspaceId));
    }

    @Operation(summary = "创建 WORKSPACE 级 Skill")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public Result<SkillResponse> createWorkspaceSkill(@Valid @RequestBody SkillCreateRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(skillService.createForWorkspace(request, workspaceId, userId), "Skill 创建成功");
    }

    @Operation(summary = "更新本工作空间的 Skill（不能修改 SYSTEM 级）")
    @PutMapping("/{name}")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public Result<SkillResponse> updateWorkspaceSkill(
            @PathVariable String name,
            @RequestBody SkillUpdateRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(skillService.updateForWorkspace(name, request, workspaceId));
    }

    @Operation(summary = "软删除本工作空间的 Skill")
    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public void deleteWorkspaceSkill(@PathVariable String name) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        skillService.deleteForWorkspace(name, workspaceId);
    }

    @Operation(summary = "切换本工作空间 Skill 的启用/禁用状态")
    @PatchMapping("/{name}/toggle")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public Result<SkillResponse> toggleWorkspaceSkill(@PathVariable String name) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(skillService.toggleForWorkspace(name, workspaceId));
    }

    @Operation(summary = "上传 Skill ZIP 包（批量导入，scope=WORKSPACE）",
            description = "上传 ZIP 文件，自动解析并 upsert 到当前工作空间。支持扁平 .md 文件和 SAA 标准目录结构。导入后自动触发缓存重载。")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public Result<SkillImportResult> importWorkspaceSkillPackage(
            @Parameter(description = "Skill ZIP 包文件（上限 20 MB）")
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.fail("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".zip")) {
            return Result.fail("仅支持 ZIP 格式文件");
        }
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        log.info("Workspace Admin 上传 Skill 包: fileName={}, size={} KB, workspaceId={}",
                originalName, file.getSize() / 1024, workspaceId);
        SkillImportResult result = skillService.importPackageForWorkspace(file.getBytes(), workspaceId, userId);
        return Result.success(result, String.format("导入完成：成功 %d/%d", result.getSuccess(), result.getTotal()));
    }
}
