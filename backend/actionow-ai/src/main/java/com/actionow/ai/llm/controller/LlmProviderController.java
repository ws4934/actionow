package com.actionow.ai.llm.controller;

import com.actionow.ai.llm.dto.LlmProviderRequest;
import com.actionow.ai.llm.dto.LlmProviderResponse;
import com.actionow.ai.llm.dto.LlmTestRequest;
import com.actionow.ai.llm.dto.LlmTestResponse;
import com.actionow.ai.llm.service.LlmProviderService;
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
 * LLM Provider 控制器
 *
 * @author Actionow
 */
@Tag(name = "LLM Provider", description = "LLM 模型配置管理")
@RestController
@RequestMapping("/llm-providers")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class LlmProviderController {

    private final LlmProviderService llmProviderService;

    @Operation(summary = "分页查询 LLM Provider")
    @GetMapping
    public Result<PageResult<LlmProviderResponse>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "厂商") @RequestParam(required = false) String provider,
            @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "模型名称") @RequestParam(required = false) String modelName) {
        return Result.success(llmProviderService.findPage(current, size, provider, enabled, modelName));
    }

    @Operation(summary = "获取所有启用的 LLM Provider")
    @GetMapping("/enabled")
    public Result<List<LlmProviderResponse>> listEnabled() {
        return Result.success(llmProviderService.findAllEnabled());
    }

    @Operation(summary = "根据 ID 获取 LLM Provider")
    @GetMapping("/{id}")
    public Result<LlmProviderResponse> getById(@PathVariable String id) {
        return Result.success(llmProviderService.getById(id));
    }

    @Operation(summary = "根据厂商查询启用的 LLM Provider")
    @GetMapping("/provider/{provider}")
    public Result<List<LlmProviderResponse>> listByProvider(@PathVariable String provider) {
        return Result.success(llmProviderService.findEnabledByProvider(provider.toUpperCase()));
    }

    @Operation(summary = "创建 LLM Provider")
    @PostMapping
    public Result<LlmProviderResponse> create(@Valid @RequestBody LlmProviderRequest request) {
        return Result.success(llmProviderService.create(request));
    }

    @Operation(summary = "更新 LLM Provider")
    @PutMapping("/{id}")
    public Result<LlmProviderResponse> update(@PathVariable String id,
                                               @Valid @RequestBody LlmProviderRequest request) {
        return Result.success(llmProviderService.update(id, request));
    }

    @Operation(summary = "删除 LLM Provider")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        llmProviderService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用 LLM Provider")
    @PutMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable String id,
                                       @RequestParam Boolean enabled) {
        llmProviderService.toggleEnabled(id, enabled);
        return Result.success();
    }

    @Operation(summary = "刷新全部缓存（含通知 agent）")
    @PostMapping("/cache/refresh")
    public Result<Void> refreshCache() {
        llmProviderService.refreshCache();
        return Result.success();
    }

    @Operation(summary = "刷新指定 Provider 缓存（含通知 agent）")
    @PostMapping("/{id}/cache/refresh")
    public Result<Void> refreshCacheById(@PathVariable String id) {
        llmProviderService.refreshCache(id);
        return Result.success();
    }

    @Operation(summary = "测试 LLM 可用性", description = "发送测试消息验证指定 LLM Provider 是否正常工作")
    @PostMapping("/{id}/test")
    public Result<LlmTestResponse> testLlm(
            @Parameter(description = "Provider ID") @PathVariable String id,
            @RequestBody(required = false) LlmTestRequest request) {
        return Result.success(llmProviderService.testLlm(id, request));
    }

    @Operation(summary = "批量测试所有启用的 LLM", description = "测试所有启用的 LLM Provider 的可用性")
    @PostMapping("/test-all")
    public Result<List<LlmTestResponse>> testAllEnabled(
            @RequestBody(required = false) LlmTestRequest request) {
        return Result.success(llmProviderService.testAllEnabled(request));
    }
}
