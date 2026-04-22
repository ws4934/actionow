package com.actionow.billing.controller;

import com.actionow.billing.dto.PlanPriceResponse;
import com.actionow.billing.dto.UpsertStripePlanRequest;
import com.actionow.billing.service.BillingPlanCatalogService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 计费管理接口（套餐目录）
 */
@RestController
@RequestMapping("/billing/admin")
@RequiredArgsConstructor
public class BillingAdminController {

    private final BillingPlanCatalogService billingPlanCatalogService;

    @PostMapping("/plans/stripe")
    @RequireWorkspaceMember(minRole = WorkspaceRole.CREATOR)
    public Result<PlanPriceResponse> upsertStripePlan(@RequestBody @Valid UpsertStripePlanRequest request) {
        String userId = UserContextHolder.getUserId();
        return Result.success(billingPlanCatalogService.upsertStripePlan(request, userId));
    }

    @GetMapping("/plans")
    @RequireWorkspaceMember(minRole = WorkspaceRole.CREATOR)
    public Result<List<PlanPriceResponse>> listPlans(@RequestParam(required = false) String provider) {
        return Result.success(billingPlanCatalogService.listPlans(provider));
    }
}

