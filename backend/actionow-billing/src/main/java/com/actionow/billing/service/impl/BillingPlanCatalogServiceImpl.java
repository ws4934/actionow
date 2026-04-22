package com.actionow.billing.service.impl;

import com.actionow.billing.config.BillingProperties;
import com.actionow.billing.dto.PlanPriceResponse;
import com.actionow.billing.dto.UpsertStripePlanRequest;
import com.actionow.billing.entity.BillingPlanPrice;
import com.actionow.billing.enums.BillingCycle;
import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.billing.mapper.BillingPlanPriceMapper;
import com.actionow.billing.service.BillingPlanCatalogService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.stripe.Stripe;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 套餐目录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingPlanCatalogServiceImpl implements BillingPlanCatalogService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String WORKSPACE_PLAN_FREE = "Free";
    private static final String WORKSPACE_PLAN_BASIC = "Basic";
    private static final String WORKSPACE_PLAN_PRO = "Pro";
    private static final String WORKSPACE_PLAN_ENTERPRISE = "Enterprise";

    private final BillingPlanPriceMapper billingPlanPriceMapper;
    private final BillingProperties billingProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanPriceResponse upsertStripePlan(UpsertStripePlanRequest request, String operatorId) {
        String provider = PaymentProvider.STRIPE.name();
        String planCode = normalizePlanCode(request.getPlanCode());
        String billingCycle = BillingCycle.from(request.getBillingCycle()).name();
        String currency = normalizeCurrency(request.getCurrency());
        String workspacePlanType = resolveWorkspacePlanType(request.getWorkspacePlanType(), planCode);

        String stripeProductId = trimToNull(request.getStripeProductId());
        String stripePriceId = trimToNull(request.getStripePriceId());
        boolean autoCreateStripe = request.getAutoCreateStripeResources() == null || request.getAutoCreateStripeResources();

        if (request.getAmountMinor() > 0 && stripePriceId == null && autoCreateStripe) {
            StripeResource resource = createStripeProductAndPrice(request, planCode, billingCycle, currency, stripeProductId);
            stripeProductId = resource.productId();
            stripePriceId = resource.priceId();
        }

        if (request.getAmountMinor() > 0 && stripePriceId == null) {
            throw new BusinessException(BillingErrorCode.PLAN_PRICE_NOT_CONFIGURED, "未提供可用的 Stripe Price ID");
        }

        BillingPlanPrice existed = billingPlanPriceMapper.selectByNaturalKey(provider, planCode, billingCycle, currency);
        BillingPlanPrice entity = existed != null ? existed : new BillingPlanPrice();
        if (existed == null) {
            entity.setId(UuidGenerator.generateUuidV7());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setCreatedBy(operatorId);
            entity.setDeleted(0);
            entity.setVersion(0);
        }

        entity.setProvider(provider);
        entity.setPlanCode(planCode);
        entity.setWorkspacePlanType(workspacePlanType);
        entity.setBillingCycle(billingCycle);
        entity.setCurrency(currency);
        entity.setAmountMinor(request.getAmountMinor());
        entity.setMetered(Boolean.TRUE.equals(request.getMetered()));
        entity.setUsageType(resolveUsageType(request));
        entity.setStripeProductId(stripeProductId);
        entity.setStripePriceId(stripePriceId);
        entity.setStatus(Boolean.FALSE.equals(request.getActive()) ? STATUS_INACTIVE : STATUS_ACTIVE);
        entity.setMeta(buildMeta(request));
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(operatorId);

        if (existed == null) {
            billingPlanPriceMapper.insert(entity);
        } else {
            billingPlanPriceMapper.updateById(entity);
        }

        return toResponse(entity);
    }

    @Override
    public List<PlanPriceResponse> listPlans(String provider) {
        List<BillingPlanPrice> entities;
        if (provider == null || provider.isBlank()) {
            entities = billingPlanPriceMapper.listAll();
        } else {
            entities = billingPlanPriceMapper.listByProvider(provider.trim().toUpperCase());
        }

        List<PlanPriceResponse> result = new ArrayList<>(entities.size());
        for (BillingPlanPrice entity : entities) {
            result.add(toResponse(entity));
        }
        return result;
    }

    private StripeResource createStripeProductAndPrice(UpsertStripePlanRequest request,
                                                       String planCode,
                                                       String billingCycle,
                                                       String currency,
                                                       String existingProductId) {
        initStripeApiKey();

        String productId = existingProductId;
        try {
            if (productId == null) {
                ProductCreateParams productParams = ProductCreateParams.builder()
                        .setName(resolveDisplayName(request, planCode))
                        .setDescription("Actionow subscription plan " + planCode)
                        .putMetadata("planCode", planCode)
                        .putMetadata("workspacePlanType", resolveWorkspacePlanType(request.getWorkspacePlanType(), planCode))
                        .build();
                Product product = Product.create(productParams);
                productId = product.getId();
            }

            PriceCreateParams.Builder priceBuilder = PriceCreateParams.builder()
                    .setProduct(productId)
                    .setCurrency(currency.toLowerCase())
                    .setUnitAmount(request.getAmountMinor())
                    .putMetadata("planCode", planCode)
                    .putMetadata("billingCycle", billingCycle)
                    .putMetadata("workspacePlanType", resolveWorkspacePlanType(request.getWorkspacePlanType(), planCode));
            if (request.getNickname() != null && !request.getNickname().isBlank()) {
                priceBuilder.setNickname(request.getNickname().trim());
            }

            PriceCreateParams.Recurring.Builder recurringBuilder = PriceCreateParams.Recurring.builder()
                    .setInterval("YEARLY".equals(billingCycle)
                            ? PriceCreateParams.Recurring.Interval.YEAR
                            : PriceCreateParams.Recurring.Interval.MONTH)
                    .setUsageType(resolveUsageType(request).equals("METERED")
                            ? PriceCreateParams.Recurring.UsageType.METERED
                            : PriceCreateParams.Recurring.UsageType.LICENSED);
            priceBuilder.setRecurring(recurringBuilder.build());

            Price price = Price.create(priceBuilder.build());
            return new StripeResource(productId, price.getId());
        } catch (Exception e) {
            log.error("创建 Stripe 套餐资源失败: planCode={}, billingCycle={}, error={}", planCode, billingCycle, e.getMessage());
            throw new BusinessException(BillingErrorCode.CALLBACK_EVENT_INVALID,
                    "创建 Stripe 套餐资源失败: " + e.getMessage());
        }
    }

    private void initStripeApiKey() {
        String apiKey = billingProperties.getStripe().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(BillingErrorCode.STRIPE_CONFIG_MISSING, "Stripe API Key 未配置");
        }
        Stripe.apiKey = apiKey;
    }

    private String resolveUsageType(UpsertStripePlanRequest request) {
        if (request.getUsageType() != null && !request.getUsageType().isBlank()) {
            return request.getUsageType().trim().toUpperCase();
        }
        return Boolean.TRUE.equals(request.getMetered()) ? "METERED" : "LICENSED";
    }

    private String resolveDisplayName(UpsertStripePlanRequest request, String planCode) {
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            return request.getDisplayName().trim();
        }
        return "Actionow " + planCode;
    }

    private String normalizePlanCode(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw new BusinessException(BillingErrorCode.PLAN_PRICE_NOT_CONFIGURED, "计划编码不能为空");
        }
        return planCode.trim().toUpperCase();
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        return currency.trim().toUpperCase();
    }

    private String resolveWorkspacePlanType(String explicitWorkspacePlanType, String planCode) {
        if (explicitWorkspacePlanType != null && !explicitWorkspacePlanType.isBlank()) {
            return explicitWorkspacePlanType.trim();
        }
        return switch (planCode) {
            case "FREE" -> WORKSPACE_PLAN_FREE;
            case "BASIC" -> WORKSPACE_PLAN_BASIC;
            case "PRO" -> WORKSPACE_PLAN_PRO;
            case "TEAM", "TEAMM", "ENTERPRISE" -> WORKSPACE_PLAN_ENTERPRISE;
            default -> planCode;
        };
    }

    private Map<String, Object> buildMeta(UpsertStripePlanRequest request) {
        Map<String, Object> meta = new HashMap<>();
        if (request.getMeta() != null && !request.getMeta().isEmpty()) {
            meta.putAll(request.getMeta());
        }
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            meta.put("displayName", request.getDisplayName().trim());
        }
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            meta.put("nickname", request.getNickname().trim());
        }
        return meta;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PlanPriceResponse toResponse(BillingPlanPrice entity) {
        return PlanPriceResponse.builder()
                .id(entity.getId())
                .provider(entity.getProvider())
                .planCode(entity.getPlanCode())
                .workspacePlanType(entity.getWorkspacePlanType())
                .billingCycle(entity.getBillingCycle())
                .currency(entity.getCurrency())
                .amountMinor(entity.getAmountMinor())
                .metered(entity.getMetered())
                .usageType(entity.getUsageType())
                .stripeProductId(entity.getStripeProductId())
                .stripePriceId(entity.getStripePriceId())
                .status(entity.getStatus())
                .meta(entity.getMeta())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private record StripeResource(String productId, String priceId) {
    }
}
