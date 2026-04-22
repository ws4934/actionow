package com.actionow.agent.controller;

import com.actionow.agent.dto.request.SkillCreateRequest;
import com.actionow.agent.dto.request.SkillUpdateRequest;
import com.actionow.agent.dto.response.SkillImportResult;
import com.actionow.agent.dto.response.SkillResponse;
import com.actionow.agent.service.AgentSkillService;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
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

/**
 * Agent Skill 管理控制器
 * 仅管理员可访问
 *
 * @author Actionow
 */
@Slf4j
@Tag(name = "Agent Skills", description = "Agent Skill 管理（OSS ZIP 包驱动 + 热重载）")
@RestController
@RequestMapping("/agent/admin/skills")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class AgentSkillController {

    private final AgentSkillService skillService;

    @Operation(summary = "创建 Skill")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<SkillResponse> createSkill(@Valid @RequestBody SkillCreateRequest request) {
        return Result.success(skillService.create(request), "Skill 创建成功");
    }

    @Operation(summary = "分页查询 Skill 列表（省略 content 字段）")
    @GetMapping
    public Result<PageResult<SkillResponse>> listSkills(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "关键词（name/description 模糊匹配）") @RequestParam(required = false) String keyword) {
        return Result.success(skillService.findPage(page, size, keyword));
    }

    @Operation(summary = "获取 Skill 详情（含 content 字段）")
    @GetMapping("/{name}")
    public Result<SkillResponse> getSkill(@PathVariable String name) {
        return Result.success(skillService.getByName(name));
    }

    @Operation(summary = "更新 Skill（可部分更新）")
    @PutMapping("/{name}")
    public Result<SkillResponse> updateSkill(
            @PathVariable String name,
            @RequestBody SkillUpdateRequest request) {
        return Result.success(skillService.update(name, request));
    }

    @Operation(summary = "删除 Skill（软删除）")
    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable String name) {
        skillService.delete(name);
    }

    @Operation(summary = "切换 Skill 启用/禁用状态")
    @PatchMapping("/{name}/toggle")
    public Result<SkillResponse> toggleSkill(@PathVariable String name) {
        return Result.success(skillService.toggle(name));
    }

    @Operation(summary = "热重载 Skill 缓存（广播至所有实例）")
    @PostMapping("/reload")
    public Result<String> reloadSkills() {
        int count = skillService.reload();
        return Result.success(String.format("已重载 %d 个 Skill", count));
    }

    @Operation(summary = "上传 Skill ZIP 包（批量导入，scope=SYSTEM）",
            description = "上传 ZIP 文件，自动解析并 upsert 到数据库。支持扁平 .md 文件和 SAA 标准目录结构。导入后自动触发缓存重载。")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SkillImportResult> importSkillPackage(
            @Parameter(description = "Skill ZIP 包文件（上限 20 MB）")
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.fail("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".zip")) {
            return Result.fail("仅支持 ZIP 格式文件");
        }
        log.info("Admin 上传 Skill 包: fileName={}, size={} KB", originalName, file.getSize() / 1024);
        SkillImportResult result = skillService.importPackage(file.getBytes());
        return Result.success(result, String.format("导入完成：成功 %d/%d", result.getSuccess(), result.getTotal()));
    }
}
