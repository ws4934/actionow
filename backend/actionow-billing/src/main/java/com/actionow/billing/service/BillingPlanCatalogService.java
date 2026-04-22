package com.actionow.billing.service;

import com.actionow.billing.dto.PlanPriceResponse;
import com.actionow.billing.dto.UpsertStripePlanRequest;

import java.util.List;

/**
 * 套餐价格目录服务
 */
public interface BillingPlanCatalogService {

    PlanPriceResponse upsertStripePlan(UpsertStripePlanRequest request, String operatorId);

    List<PlanPriceResponse> listPlans(String provider);
}

