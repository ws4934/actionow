package com.actionow.ai.controller;

import com.actionow.ai.dto.PromptTemplateRequest;
import com.actionow.ai.dto.PromptTemplateResponse;
import com.actionow.ai.service.PromptTemplateService;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板控制器
 *
 * @author Actionow
 */
@Tag(name = "提示词模板", description = "提示词模板管理接口")
@RestController
@RequestMapping("/ai/template")
@RequiredArgsConstructor
@RequireWorkspaceMember
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @Operation(summary = "创建模板")
    @PostMapping
    public Result<PromptTemplateResponse> create(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @Valid @RequestBody PromptTemplateRequest request) {
        String creatorId = SecurityUtils.getCurrentUserId();
        PromptTemplateResponse response = promptTemplateService.create(workspaceId, request, creatorId);
        return Result.success(response);
    }

    @Operation(summary = "更新模板")
    @PutMapping("/{templateId}")
    public Result<PromptTemplateResponse> update(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @PathVariable String templateId,
            @Valid @RequestBody PromptTemplateRequest request) {
        PromptTemplateResponse response = promptTemplateService.update(workspaceId, templateId, request);
        return Result.success(response);
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{templateId}")
    public Result<Void> delete(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @PathVariable String templateId) {
        promptTemplateService.delete(workspaceId, templateId);
        return Result.success();
    }

    @Operation(summary = "获取模板详情")
    @GetMapping("/{templateId}")
    public Result<PromptTemplateResponse> getById(@PathVariable String templateId) {
        PromptTemplateResponse response = promptTemplateService.getById(templateId);
        return Result.success(response);
    }

    @Operation(summary = "获取可用模板列表")
    @GetMapping("/list")
    public Result<List<PromptTemplateResponse>> listAvailable(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestParam String type) {
        List<PromptTemplateResponse> templates = promptTemplateService.listAvailable(workspaceId, type);
        return Result.success(templates);
    }

    @Operation(summary = "获取系统模板列表")
    @GetMapping("/system")
    public Result<List<PromptTemplateResponse>> listSystem(@RequestParam String type) {
        List<PromptTemplateResponse> templates = promptTemplateService.listSystemTemplates(type);
        return Result.success(templates);
    }

    @Operation(summary = "预览模板渲染结果")
    @PostMapping("/{templateId}/preview")
    public Result<String> preview(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> variables) {
        String rendered = promptTemplateService.renderTemplate(templateId, variables);
        return Result.success(rendered);
    }
}
