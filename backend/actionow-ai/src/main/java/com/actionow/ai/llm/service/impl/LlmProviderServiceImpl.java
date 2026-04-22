package com.actionow.ai.llm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.ai.feign.SystemFeignClient;
import com.actionow.ai.llm.cache.LlmProviderCacheService;
import com.actionow.ai.llm.dto.LlmCredentialsResponse;
import com.actionow.ai.llm.dto.LlmProviderRequest;
import com.actionow.ai.llm.dto.LlmProviderResponse;
import com.actionow.ai.llm.dto.LlmTestRequest;
import com.actionow.ai.llm.dto.LlmTestResponse;
import com.actionow.ai.llm.entity.LlmProvider;
import com.actionow.ai.llm.mapper.LlmProviderMapper;
import com.actionow.ai.llm.service.LlmProviderService;
import com.actionow.ai.llm.service.LlmTestService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LLM Provider 服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderServiceImpl implements LlmProviderService {

    private static final String CHANNEL_LLM_EVICT = "llm:cache:evict";

    private final LlmProviderMapper mapper;
    private final LlmProviderCacheService cacheService;
    private final SystemFeignClient systemFeignClient;
    private final LlmTestService llmTestService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmProviderResponse create(LlmProviderRequest request) {
        // 检查是否已存在
        LlmProvider existing = mapper.selectByProviderAndModelId(request.getProvider(), request.getModelId());
        if (existing != null) {
            throw new BusinessException("LLM Provider 已存在: " + request.getProvider() + "/" + request.getModelId());
        }

        LlmProvider provider = new LlmProvider();
        mapRequestToEntity(request, provider);

        mapper.insert(provider);

        log.info("创建 LLM Provider: id={}, provider={}, modelId={}",
                provider.getId(), provider.getProvider(), provider.getModelId());

        // 刷新缓存并通知 agent 清除对应的 ChatModel 缓存
        cacheService.refreshAll();
        stringRedisTemplate.convertAndSend(CHANNEL_LLM_EVICT, provider.getId());

        return LlmProviderResponse.fromEntity(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmProviderResponse update(String id, LlmProviderRequest request) {
        LlmProvider provider = mapper.selectById(id);
        if (provider == null || provider.getDeleted() != 0) {
            throw new BusinessException("LLM Provider 不存在: " + id);
        }

        // 如果修改了 provider 或 modelId，检查是否重复
        if ((request.getProvider() != null && !request.getProvider().equals(provider.getProvider())) ||
                (request.getModelId() != null && !request.getModelId().equals(provider.getModelId()))) {
            String newProvider = request.getProvider() != null ? request.getProvider() : provider.getProvider();
            String newModelId = request.getModelId() != null ? request.getModelId() : provider.getModelId();
            LlmProvider existing = mapper.selectByProviderAndModelId(newProvider, newModelId);
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException("LLM Provider 已存在: " + newProvider + "/" + newModelId);
            }
        }

        mapRequestToEntity(request, provider);
        mapper.updateById(provider);

        log.info("更新 LLM Provider: id={}", id);

        // 刷新缓存并通知 agent 清除对应的 ChatModel 缓存
        cacheService.evict(id);
        stringRedisTemplate.convertAndSend(CHANNEL_LLM_EVICT, id);

        return LlmProviderResponse.fromEntity(provider);
    }

    @Override
    public Optional<LlmProviderResponse> findById(String id) {
        return cacheService.get(id).map(LlmProviderResponse::fromEntity);
    }

    @Override
    public LlmProviderResponse getById(String id) {
        return findById(id).orElseThrow(() -> new BusinessException("LLM Provider 不存在: " + id));
    }

    @Override
    public LlmProvider getEntityById(String id) {
        return cacheService.get(id).orElseThrow(() -> new BusinessException("LLM Provider 不存在: " + id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        LlmProvider provider = mapper.selectById(id);
        if (provider == null || provider.getDeleted() != 0) {
            throw new BusinessException("LLM Provider 不存在: " + id);
        }

        mapper.deleteById(id);

        log.info("删除 LLM Provider: id={}", id);

        // 刷新缓存并通知 agent 清除对应的 ChatModel 缓存
        cacheService.evict(id);
        stringRedisTemplate.convertAndSend(CHANNEL_LLM_EVICT, id);
    }

    @Override
    public List<LlmProviderResponse> findAllEnabled() {
        return cacheService.getAllEnabled();
    }

    @Override
    public PageResult<LlmProviderResponse> findPage(Long current, Long size, String provider, Boolean enabled, String modelName) {
        Page<LlmProvider> page = new Page<>(current, size);
        IPage<LlmProvider> resultPage = mapper.selectPage(page, provider, enabled, modelName);

        List<LlmProviderResponse> records = resultPage.getRecords().stream()
                .map(LlmProviderResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(current, size, resultPage.getTotal(), records);
    }

    @Override
    public List<LlmProviderResponse> findEnabledByProvider(String provider) {
        return cacheService.getEnabledByProvider(provider);
    }

    @Override
    public Optional<LlmProviderResponse> findByProviderAndModelId(String provider, String modelId) {
        LlmProvider entity = mapper.selectByProviderAndModelId(provider, modelId);
        if (entity != null && entity.getDeleted() == 0) {
            return Optional.of(LlmProviderResponse.fromEntity(entity));
        }
        return Optional.empty();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        toggleEnabled(id, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        toggleEnabled(id, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(String id, Boolean enabled) {
        LlmProvider provider = mapper.selectById(id);
        if (provider == null || provider.getDeleted() != 0) {
            throw new BusinessException("LLM Provider 不存在: " + id);
        }

        provider.setEnabled(enabled);
        mapper.updateById(provider);

        // 刷新缓存并通知 agent 清除对应的 ChatModel 缓存
        cacheService.evict(id);
        stringRedisTemplate.convertAndSend(CHANNEL_LLM_EVICT, id);

        log.info("切换 LLM Provider 启用状态: id={}, enabled={}", id, enabled);
    }

    @Override
    public void refreshCache() {
        cacheService.refreshAll();
        stringRedisTemplate.convertAndSend(CHANNEL_LLM_EVICT, "all");
        log.info("强制刷新所有 LLM Provider 缓存，已通知 agent");
    }

    @Override
    public void refreshCache(String id) {
        cacheService.refresh(id);
        stringRedisTemplate.convertAndSend(CHANNEL_LLM_EVICT, id);
        log.info("强制刷新 LLM Provider 缓存: id={}, 已通知 agent", id);
    }

    /**
     * 映射请求到实体
     */
    private void mapRequestToEntity(LlmProviderRequest request, LlmProvider entity) {
        if (request.getProvider() != null) {
            entity.setProvider(request.getProvider().toUpperCase());
        }
        if (request.getModelId() != null) {
            entity.setModelId(request.getModelId());
        }
        if (request.getModelName() != null) {
            entity.setModelName(request.getModelName());
        }
        if (request.getTemperature() != null) {
            entity.setTemperature(request.getTemperature());
        } else if (entity.getTemperature() == null) {
            entity.setTemperature(new BigDecimal("0.7"));
        }
        if (request.getMaxOutputTokens() != null) {
            entity.setMaxOutputTokens(request.getMaxOutputTokens());
        } else if (entity.getMaxOutputTokens() == null) {
            entity.setMaxOutputTokens(8192);
        }
        if (request.getTopP() != null) {
            entity.setTopP(request.getTopP());
        } else if (entity.getTopP() == null) {
            entity.setTopP(new BigDecimal("0.95"));
        }
        if (request.getTopK() != null) {
            entity.setTopK(request.getTopK());
        } else if (entity.getTopK() == null) {
            entity.setTopK(40);
        }
        if (request.getApiEndpoint() != null) {
            entity.setApiEndpoint(request.getApiEndpoint());
        }
        if (request.getCompletionsPath() != null) {
            entity.setCompletionsPath(request.getCompletionsPath());
        }
        if (request.getApiKeyRef() != null) {
            entity.setApiKeyRef(request.getApiKeyRef());
        }
        if (request.getApiEndpointRef() != null) {
            entity.setApiEndpointRef(request.getApiEndpointRef());
        }
        if (request.getExtraConfig() != null) {
            entity.setExtraConfig(request.getExtraConfig());
        }
        if (request.getContextWindow() != null) {
            entity.setContextWindow(request.getContextWindow());
        } else if (entity.getContextWindow() == null) {
            entity.setContextWindow(128000);
        }
        if (request.getMaxInputTokens() != null) {
            entity.setMaxInputTokens(request.getMaxInputTokens());
        } else if (entity.getMaxInputTokens() == null) {
            entity.setMaxInputTokens(100000);
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        } else if (entity.getPriority() == null) {
            entity.setPriority(0);
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
    }

    @Override
    public Optional<LlmCredentialsResponse> getCredentials(String id) {
        Optional<LlmProvider> providerOpt = cacheService.get(id);
        if (providerOpt.isEmpty()) {
            return Optional.empty();
        }

        LlmProvider provider = providerOpt.get();

        // 解析 apiKeyRef 获取实际的 API Key
        String apiKey = null;
        if (StringUtils.hasText(provider.getApiKeyRef())) {
            try {
                Result<String> result = systemFeignClient.getConfigValue(provider.getApiKeyRef());
                if (result.isSuccess() && StringUtils.hasText(result.getData())) {
                    apiKey = result.getData();
                } else {
                    log.warn("无法获取 API Key: apiKeyRef={}, result={}", provider.getApiKeyRef(), result);
                }
            } catch (Exception e) {
                log.error("调用 System 服务获取 API Key 失败: apiKeyRef={}", provider.getApiKeyRef(), e);
            }
        }

        // 解析 apiEndpointRef 获取实际的 API Endpoint
        String apiEndpoint = provider.getApiEndpoint();
        if (StringUtils.hasText(provider.getApiEndpointRef())) {
            try {
                Result<String> result = systemFeignClient.getConfigValue(provider.getApiEndpointRef());
                if (result.isSuccess() && StringUtils.hasText(result.getData())) {
                    apiEndpoint = result.getData();
                }
            } catch (Exception e) {
                log.warn("调用 System 服务获取 API Endpoint 失败: apiEndpointRef={}", provider.getApiEndpointRef(), e);
            }
        }

        LlmCredentialsResponse credentials = LlmCredentialsResponse.builder()
                .id(provider.getId())
                .provider(provider.getProvider())
                .modelId(provider.getModelId())
                .modelName(provider.getModelName())
                .apiKey(apiKey)
                .apiEndpoint(apiEndpoint)
                .completionsPath(provider.getCompletionsPath())
                .temperature(provider.getTemperature())
                .maxOutputTokens(provider.getMaxOutputTokens())
                .topP(provider.getTopP())
                .topK(provider.getTopK())
                .contextWindow(provider.getContextWindow())
                .maxInputTokens(provider.getMaxInputTokens())
                .extraConfig(provider.getExtraConfig())
                .build();

        return Optional.of(credentials);
    }

    @Override
    public LlmTestResponse testLlm(String id, LlmTestRequest request) {
        log.info("测试 LLM Provider: id={}", id);

        // 获取凭证
        Optional<LlmCredentialsResponse> credentialsOpt = getCredentials(id);
        if (credentialsOpt.isEmpty()) {
            return LlmTestResponse.fail(id, null, null, null, "NOT_FOUND",
                    "LLM Provider 不存在: " + id, null);
        }

        LlmCredentialsResponse credentials = credentialsOpt.get();

        // 执行测试
        LlmTestResponse result = llmTestService.testLlm(credentials, request);

        log.info("LLM 测试完成: id={}, provider={}, modelId={}, success={}, responseTime={}ms",
                id, credentials.getProvider(), credentials.getModelId(),
                result.getSuccess(), result.getResponseTimeMs());

        return result;
    }

    @Override
    public List<LlmTestResponse> testAllEnabled(LlmTestRequest request) {
        log.info("批量测试所有启用的 LLM Provider");

        List<LlmProviderResponse> enabledProviders = findAllEnabled();
        List<LlmTestResponse> results = new ArrayList<>();

        for (LlmProviderResponse provider : enabledProviders) {
            try {
                LlmTestResponse result = testLlm(provider.getId(), request);
                results.add(result);
            } catch (Exception e) {
                log.error("测试 LLM Provider 失败: id={}", provider.getId(), e);
                results.add(LlmTestResponse.fail(
                        provider.getId(), provider.getProvider(), provider.getModelId(),
                        provider.getModelName(), "EXCEPTION", e.getMessage(), null));
            }
        }

        long successCount = results.stream().filter(LlmTestResponse::getSuccess).count();
        log.info("批量测试完成: 总数={}, 成功={}, 失败={}",
                results.size(), successCount, results.size() - successCount);

        return results;
    }
}
