package com.actionow.system.controller;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.system.dto.DictItemResponse;
import com.actionow.system.dto.DictTypeResponse;
import com.actionow.system.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典控制器
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/system/dicts")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class DictController {

    private final DictService dictService;

    /**
     * 获取所有字典类型
     */
    @GetMapping("/types")
    public Result<List<DictTypeResponse>> listAllTypes() {
        return Result.success(dictService.listAllTypes());
    }

    /**
     * 获取字典类型详情（含字典项）
     */
    @GetMapping("/types/{typeCode}")
    public Result<DictTypeResponse> getTypeByCode(@PathVariable String typeCode) {
        return Result.success(dictService.getTypeByCode(typeCode));
    }

    /**
     * 获取字典项列表
     */
    @GetMapping("/items/{typeCode}")
    public Result<List<DictItemResponse>> listItemsByTypeCode(@PathVariable String typeCode) {
        return Result.success(dictService.listItemsByTypeCode(typeCode));
    }

    /**
     * 获取字典项
     */
    @GetMapping("/items/{typeCode}/{itemCode}")
    public Result<DictItemResponse> getItem(@PathVariable String typeCode, @PathVariable String itemCode) {
        return Result.success(dictService.getItem(typeCode, itemCode));
    }

    /**
     * 获取默认字典项
     */
    @GetMapping("/items/{typeCode}/default")
    public Result<DictItemResponse> getDefaultItem(@PathVariable String typeCode) {
        return Result.success(dictService.getDefaultItem(typeCode));
    }

    /**
     * 刷新字典缓存
     */
    @PostMapping("/refresh-cache")
    public Result<Void> refreshCache() {
        dictService.refreshCache();
        return Result.success();
    }
}
