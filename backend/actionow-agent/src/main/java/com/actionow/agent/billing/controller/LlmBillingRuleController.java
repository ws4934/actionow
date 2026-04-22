package com.actionow.agent.billing.controller;

import com.actionow.agent.billing.dto.LlmBillingRuleRequest;
import com.actionow.agent.billing.dto.LlmBillingRuleResponse;
import com.actionow.agent.billing.service.LlmBillingRuleService;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LLM 计费规则控制器
 *
 * @author Actionow
 */
@Tag(name = "LLM Billing Rule", description = "LLM 计费规则管理")
@RestController
@RequestMapping("/agent/llm-billing-rules")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class LlmBillingRuleController {

    private final LlmBillingRuleService llmBillingRuleService;

    @Operation(summary = "分页查询 LLM 计费规则")
    @GetMapping
    public Result<PageResult<LlmBillingRuleResponse>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "LLM Provider ID") @RequestParam(required = false) String llmProviderId,
            @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled) {
        return Result.success(llmBillingRuleService.findPage(current, size, llmProviderId, enabled));
    }

    @Operation(summary = "获取所有当前有效的计费规则")
    @GetMapping("/effective")
    public Result<List<LlmBillingRuleResponse>> listEffective() {
        return Result.success(llmBillingRuleService.getAllEffectiveRules());
    }

    @Operation(summary = "根据 ID 获取 LLM 计费规则")
    @GetMapping("/{id}")
    public Result<LlmBillingRuleResponse> getById(@PathVariable String id) {
        return Result.success(llmBillingRuleService.getById(id));
    }

    @Operation(summary = "根据 LLM Provider ID 查询计费规则")
    @GetMapping("/provider/{llmProviderId}")
    public Result<List<LlmBillingRuleResponse>> listByProviderId(@PathVariable String llmProviderId) {
        return Result.success(llmBillingRuleService.getByProviderId(llmProviderId));
    }

    @Operation(summary = "获取 LLM Provider 当前有效的计费规则")
    @GetMapping("/provider/{llmProviderId}/effective")
    public Result<LlmBillingRuleResponse> getEffective(@PathVariable String llmProviderId) {
        return llmBillingRuleService.getEffectiveRule(llmProviderId)
                .map(Result::success)
                .orElse(Result.success(null));
    }

    @Operation(summary = "创建 LLM 计费规则")
    @PostMapping
    public Result<LlmBillingRuleResponse> create(@Valid @RequestBody LlmBillingRuleRequest request) {
        return Result.success(llmBillingRuleService.create(request));
    }

    @Operation(summary = "更新 LLM 计费规则")
    @PutMapping("/{id}")
    public Result<LlmBillingRuleResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody LlmBillingRuleRequest request) {
        return Result.success(llmBillingRuleService.update(id, request));
    }

    @Operation(summary = "删除 LLM 计费规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        llmBillingRuleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用 LLM 计费规则")
    @PutMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable String id,
                                       @RequestParam Boolean enabled) {
        llmBillingRuleService.toggleEnabled(id, enabled);
        return Result.success();
    }

    @Operation(summary = "刷新缓存")
    @PostMapping("/cache/refresh")
    public Result<Void> refreshCache(@RequestParam(required = false) String llmProviderId) {
        if (llmProviderId != null) {
            llmBillingRuleService.refreshCache(llmProviderId);
        } else {
            llmBillingRuleService.refreshAllCaches();
        }
        return Result.success();
    }
}
