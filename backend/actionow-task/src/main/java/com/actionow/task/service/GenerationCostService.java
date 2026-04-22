package com.actionow.task.service;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.task.dto.AssetInfoResponse;
import com.actionow.task.dto.AvailableProviderResponse;
import com.actionow.task.feign.AiFeignClient;
import com.actionow.task.feign.AssetFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 生成积分计算与提供商查询服务
 * 从 AiGenerationOrchestrator 抽取，负责：
 * - 积分动态计算（调用 AI 服务 estimate-cost）
 * - 引用素材校验
 * - 可用提供商查询
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationCostService {

    private static final Set<String> FILE_PARAM_TYPES = Set.of(
            "IMAGE", "VIDEO", "AUDIO", "DOCUMENT",
            "IMAGE_LIST", "VIDEO_LIST", "AUDIO_LIST", "DOCUMENT_LIST"
    );

    private final AiFeignClient aiFeignClient;
    private final AssetFeignClient assetFeignClient;

    /**
     * 获取可用的 AI 模型提供商列表
     */
    public List<AvailableProviderResponse> getAvailableProviders(String providerType) {
        Result<List<AvailableProviderResponse>> result = aiFeignClient.getAvailableProviders(providerType);
        if (result.isSuccess() && result.getData() != null) {
            return result.getData();
        }
        return Collections.emptyList();
    }

    /**
     * 预估积分消耗（公开 API）
     */
    public Map<String, Object> estimateCost(String providerId, Map<String, Object> params) {
        Result<Map<String, Object>> result = aiFeignClient.estimateCost(providerId, params);
        if (result.isSuccess() && result.getData() != null) {
            return result.getData();
        }
        // 回退：从 provider 静态值获取
        Result<AvailableProviderResponse> providerResult = aiFeignClient.getProviderDetail(providerId);
        if (providerResult.isSuccess() && providerResult.getData() != null) {
            Long creditCost = providerResult.getData().getCreditCost();
            return Map.of("finalCost", creditCost != null ? creditCost : 10L, "source", "static");
        }
        return Map.of("finalCost", 10L, "source", "default");
    }

    /**
     * 预估积分消耗（动态定价）
     * 调用 AI 服务的 estimate-cost 接口计算实际积分，失败时回退到静态值
     */
    public Long estimateCreditCost(String providerId, Map<String, Object> params,
                                    AvailableProviderResponse provider) {
        try {
            Result<Map<String, Object>> estimateResult = aiFeignClient.estimateCost(providerId, params);
            if (estimateResult.isSuccess() && estimateResult.getData() != null) {
                Object finalCost = estimateResult.getData().get("finalCost");
                if (finalCost instanceof Number) {
                    long cost = ((Number) finalCost).longValue();
                    log.debug("动态积分计算结果: providerId={}, finalCost={}, source={}",
                            providerId, cost, estimateResult.getData().get("source"));
                    return cost;
                }
            }
            log.warn("动态积分计算返回无效结果，回退到静态值: providerId={}", providerId);
        } catch (Exception e) {
            log.warn("动态积分计算失败，回退到静态值: providerId={}, error={}", providerId, e.getMessage());
        }
        return provider.getCreditCost() != null ? provider.getCreditCost() : 10L;
    }

    /**
     * 校验引用素材是否存在
     */
    public void validateReferencedAssets(Map<String, Object> params, AvailableProviderResponse provider) {
        if (provider == null || CollectionUtils.isEmpty(provider.getInputSchema()) || CollectionUtils.isEmpty(params)) {
            return;
        }

        Set<String> referencedAssetIds = new LinkedHashSet<>();
        for (Map<String, Object> schemaField : provider.getInputSchema()) {
            if (schemaField == null) {
                continue;
            }

            String fieldName = asString(schemaField.get("name"));
            String fieldType = asString(schemaField.get("type"));
            if (!StringUtils.hasText(fieldName) || !FILE_PARAM_TYPES.contains(fieldType)) {
                continue;
            }

            Object value = params.get(fieldName);
            collectAssetIds(value, referencedAssetIds);
        }

        if (referencedAssetIds.isEmpty()) {
            return;
        }

        List<String> assetIds = new ArrayList<>(referencedAssetIds);
        Result<List<AssetInfoResponse>> result = assetFeignClient.batchGetAssets(assetIds);
        if (!result.isSuccess() || result.getData() == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "校验引用素材失败: " + result.getMessage());
        }

        Set<String> foundIds = result.getData().stream()
                .map(AssetInfoResponse::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        List<String> missingIds = assetIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "引用素材不存在或已删除: " + String.join(", ", missingIds));
        }
    }

    private void collectAssetIds(Object value, Set<String> assetIds) {
        if (value instanceof String strValue) {
            if (isAssetId(strValue)) {
                assetIds.add(strValue);
            }
            return;
        }

        if (value instanceof List<?> listValue) {
            for (Object item : listValue) {
                if (item instanceof String strItem && isAssetId(strItem)) {
                    assetIds.add(strItem);
                }
            }
        }
    }

    private boolean isAssetId(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) {
            return false;
        }
        return value.length() <= 100 && value.matches("^[0-9a-fA-F-]{36}$");
    }

    private String asString(Object value) {
        return value instanceof String str ? str : null;
    }
}
