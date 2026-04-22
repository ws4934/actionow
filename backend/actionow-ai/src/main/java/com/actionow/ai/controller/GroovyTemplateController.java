package com.actionow.ai.controller;

import com.actionow.ai.dto.GroovyTemplateRequest;
import com.actionow.ai.dto.GroovyTemplateResponse;
import com.actionow.ai.plugin.groovy.GroovyScriptValidator;
import com.actionow.ai.service.GroovyTemplateService;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Groovy模板控制器
 * 管理Groovy脚本模板
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/groovy-templates")
@RequiredArgsConstructor
public class GroovyTemplateController {

    private final GroovyTemplateService groovyTemplateService;

    /**
     * 分页查询模板列表
     *
     * @param pageNum        页码（从1开始）
     * @param pageSize       每页大小
     * @param templateType   模板类型（可选）
     * @param generationType 生成类型（可选）
     * @param isSystem       是否系统模板（可选）
     * @param name           名称模糊搜索（可选）
     * @return 分页模板列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<PageResult<GroovyTemplateResponse>> listPage(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String generationType,
            @RequestParam(required = false) Boolean isSystem,
            @RequestParam(required = false) String name) {
        PageResult<GroovyTemplateResponse> pageResult = groovyTemplateService.listTemplatesPage(
                pageNum, pageSize, templateType, generationType, isSystem, name);
        return Result.success(pageResult);
    }

    /**
     * 获取模板列表（全量，无分页）
     *
     * @param templateType   模板类型（可选）
     * @param generationType 生成类型（可选）
     * @param isSystem       是否系统模板（可选）
     * @return 模板列表
     */
    @GetMapping("/all")
    @RequireWorkspaceMember
    public Result<List<GroovyTemplateResponse>> listAll(
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String generationType,
            @RequestParam(required = false) Boolean isSystem) {
        List<GroovyTemplateResponse> templates = groovyTemplateService.listTemplates(
                templateType, generationType, isSystem);
        return Result.success(templates);
    }

    /**
     * 获取系统模板列表
     *
     * @return 系统模板列表
     */
    @GetMapping("/system")
    @RequireWorkspaceMember
    public Result<List<GroovyTemplateResponse>> listSystemTemplates() {
        List<GroovyTemplateResponse> templates = groovyTemplateService.listSystemTemplates();
        return Result.success(templates);
    }

    /**
     * 获取模板详情
     *
     * @param id 模板ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceMember
    public Result<GroovyTemplateResponse> getById(@PathVariable String id) {
        GroovyTemplateResponse template = groovyTemplateService.getById(id);
        return Result.success(template);
    }

    /**
     * 根据名称获取模板
     *
     * @param name 模板名称
     * @return 模板详情
     */
    @GetMapping("/name/{name}")
    @RequireWorkspaceMember
    public Result<GroovyTemplateResponse> getByName(@PathVariable String name) {
        GroovyTemplateResponse template = groovyTemplateService.getByName(name);
        return Result.success(template);
    }

    /**
     * 创建模板
     *
     * @param request 创建请求
     * @return 创建的模板
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public Result<GroovyTemplateResponse> create(@Valid @RequestBody GroovyTemplateRequest request) {
        GroovyTemplateResponse template = groovyTemplateService.create(request);
        return Result.success(template);
    }

    /**
     * 更新模板
     *
     * @param id      模板ID
     * @param request 更新请求
     * @return 更新后的模板
     */
    @PutMapping("/{id}")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public Result<GroovyTemplateResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody GroovyTemplateRequest request) {
        GroovyTemplateResponse template = groovyTemplateService.update(id, request);
        return Result.success(template);
    }

    /**
     * 删除模板
     *
     * @param id 模板ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    public Result<Void> delete(@PathVariable String id) {
        groovyTemplateService.delete(id);
        return Result.success();
    }

    /**
     * 验证脚本语法
     *
     * @param request 验证请求
     * @return 验证结果
     */
    @PostMapping("/validate")
    @RequireWorkspaceMember
    public Result<GroovyScriptValidator.ValidationResult> validateScript(
            @RequestBody Map<String, String> request) {
        String scriptContent = request.get("scriptContent");
        GroovyScriptValidator.ValidationResult result = groovyTemplateService.validateScript(scriptContent);
        return Result.success(result);
    }

    /**
     * 测试脚本执行
     *
     * @param request 测试请求
     * @return 执行结果
     */
    @PostMapping("/test")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.ADMIN)
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> testScript(@RequestBody Map<String, Object> request) {
        String scriptContent = (String) request.get("scriptContent");
        String templateType = (String) request.get("templateType"); // REQUEST_BUILDER, RESPONSE_MAPPER, CUSTOM_LOGIC

        // 支持两种输入格式：
        // 1. 直接的 inputs/config/response 字段
        // 2. 包装在 testInputs 中的 inputs/config/response
        Map<String, Object> inputs = null;
        Map<String, Object> config = null;
        Object response = null;

        Object testInputsObj = request.get("testInputs");
        if (testInputsObj instanceof Map<?, ?> testInputs) {
            inputs = (Map<String, Object>) testInputs.get("inputs");
            config = (Map<String, Object>) testInputs.get("config");
            response = testInputs.get("response");
        }

        // 如果 testInputs 中没有，则从顶层取
        if (inputs == null) {
            inputs = (Map<String, Object>) request.get("inputs");
        }
        if (config == null) {
            config = (Map<String, Object>) request.get("config");
        }
        if (response == null) {
            response = request.get("response");
        }

        Map<String, Object> result = groovyTemplateService.testScript(scriptContent, templateType, inputs, config, response);
        return Result.success(result);
    }
}
