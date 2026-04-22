package com.actionow.ai.controller;

import com.actionow.ai.dto.SchemaValidationResponse;
import com.actionow.ai.service.schema.InputSchemaService;
import com.actionow.ai.service.schema.SchemaValidator;
import com.actionow.ai.service.schema.SchemaTemplateService;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 表单Schema接口
 * 提供给前端动态渲染表单的API
 *
 * @author Actionow
 */
@Tag(name = "表单Schema", description = "动态表单Schema查询和验证")
@RestController
@RequestMapping("/form-schema")
@RequiredArgsConstructor
@Validated
@RequireSystemTenant(minRole = "ADMIN")
public class FormSchemaController {

    private final InputSchemaService inputSchemaService;
    private final SchemaTemplateService schemaTemplateService;

    @Operation(summary = "获取模型提供商的表单Schema")
    @GetMapping("/provider/{providerId}")
    public Result<InputSchemaService.FormSchema> getFormSchema(
            @Parameter(description = "模型提供商ID") @PathVariable String providerId) {
        InputSchemaService.FormSchema schema = inputSchemaService.getFormSchema(providerId);
        if (schema == null) {
            return Result.fail("模型提供商不存在");
        }
        return Result.success(schema);
    }

    @Operation(summary = "验证表单输入")
    @PostMapping("/provider/{providerId}/validate")
    public Result<SchemaValidationResponse> validateInput(
            @Parameter(description = "模型提供商ID") @PathVariable String providerId,
            @RequestBody Map<String, Object> input) {
        SchemaValidator.ValidationResult result = inputSchemaService.validateInput(providerId, input);
        return Result.success(SchemaValidationResponse.from(result));
    }

    @Operation(summary = "合并默认值")
    @PostMapping("/provider/{providerId}/merge-defaults")
    public Result<Map<String, Object>> mergeDefaults(
            @Parameter(description = "模型提供商ID") @PathVariable String providerId,
            @RequestBody(required = false) Map<String, Object> input) {
        Map<String, Object> merged = inputSchemaService.mergeWithDefaults(providerId, input);
        return Result.success(merged);
    }

    @Operation(summary = "获取必填参数列表")
    @GetMapping("/provider/{providerId}/required-params")
    public Result<List<String>> getRequiredParams(
            @Parameter(description = "模型提供商ID") @PathVariable String providerId) {
        List<String> requiredParams = inputSchemaService.getRequiredParams(providerId);
        return Result.success(requiredParams);
    }

    // ========== Schema模板相关 ==========

    @Operation(summary = "获取所有Schema模板")
    @GetMapping("/templates")
    public Result<List<SchemaTemplateService.SchemaTemplate>> getTemplates(
            @Parameter(description = "生成类型过滤") @RequestParam(required = false) String providerType) {
        List<SchemaTemplateService.SchemaTemplate> templates = schemaTemplateService.getTemplates(providerType);
        return Result.success(templates);
    }

    @Operation(summary = "获取Schema模板详情")
    @GetMapping("/templates/{templateId}")
    public Result<SchemaTemplateService.SchemaTemplate> getTemplate(
            @Parameter(description = "模板ID") @PathVariable String templateId) {
        SchemaTemplateService.SchemaTemplate template = schemaTemplateService.getTemplate(templateId);
        if (template == null) {
            return Result.fail("模板不存在");
        }
        return Result.success(template);
    }

    @Operation(summary = "应用Schema模板到模型提供商")
    @PostMapping("/templates/{templateId}/apply/{providerId}")
    public Result<Boolean> applyTemplate(
            @Parameter(description = "模板ID") @PathVariable String templateId,
            @Parameter(description = "模型提供商ID") @PathVariable String providerId) {
        boolean success = schemaTemplateService.applyTemplate(templateId, providerId);
        return success ? Result.success(true) : Result.fail("应用模板失败");
    }
}
