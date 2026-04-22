package com.actionow.agent.billing.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.billing.cache.LlmBillingRuleCacheService;
import com.actionow.agent.billing.dto.LlmBillingRuleRequest;
import com.actionow.agent.billing.dto.LlmBillingRuleResponse;
import com.actionow.agent.billing.entity.LlmBillingRule;
import com.actionow.agent.billing.mapper.LlmBillingRuleMapper;
import com.actionow.agent.billing.service.LlmBillingRuleService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LLM 计费规则服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmBillingRuleServiceImpl implements LlmBillingRuleService {

    private final LlmBillingRuleMapper mapper;
    private final LlmBillingRuleCacheService cacheService;

    @Override
    public Optional<LlmBillingRuleResponse> getEffectiveRule(String llmProviderId) {
        return cacheService.getEffective(llmProviderId)
                .map(LlmBillingRuleResponse::fromEntity);
    }

    @Override
    public Optional<LlmBillingRule> getEffectiveRuleEntity(String llmProviderId) {
        return cacheService.getEffective(llmProviderId);
    }

    @Override
    public List<LlmBillingRuleResponse> getAllEffectiveRules() {
        return cacheService.getAllEffective().stream()
                .map(LlmBillingRuleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<LlmBillingRuleResponse> findPage(Long current, Long size, String llmProviderId, Boolean enabled) {
        Page<LlmBillingRule> page = new Page<>(current, size);
        IPage<LlmBillingRule> resultPage = mapper.selectPage(page, llmProviderId, enabled);

        List<LlmBillingRuleResponse> records = resultPage.getRecords().stream()
                .map(LlmBillingRuleResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(current, size, resultPage.getTotal(), records);
    }

    @Override
    public LlmBillingRuleResponse getById(String id) {
        LlmBillingRule rule = mapper.selectById(id);
        if (rule == null || rule.getDeleted() != 0) {
            throw new BusinessException("LLM 计费规则不存在: " + id);
        }
        return LlmBillingRuleResponse.fromEntity(rule);
    }

    @Override
    public List<LlmBillingRuleResponse> getByProviderId(String llmProviderId) {
        return mapper.selectByProviderId(llmProviderId).stream()
                .map(LlmBillingRuleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmBillingRuleResponse create(LlmBillingRuleRequest request) {
        LlmBillingRule rule = new LlmBillingRule();
        mapRequestToEntity(request, rule);

        // 设置默认生效时间
        if (rule.getEffectiveFrom() == null) {
            rule.setEffectiveFrom(LocalDateTime.now());
        }

        mapper.insert(rule);

        log.info("创建 LLM 计费规则: id={}, llmProviderId={}",
                rule.getId(), rule.getLlmProviderId());

        // 刷新缓存
        cacheService.evict(rule.getLlmProviderId());

        return LlmBillingRuleResponse.fromEntity(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmBillingRuleResponse update(String id, LlmBillingRuleRequest request) {
        LlmBillingRule rule = mapper.selectById(id);
        if (rule == null || rule.getDeleted() != 0) {
            throw new BusinessException("LLM 计费规则不存在: " + id);
        }

        String oldProviderId = rule.getLlmProviderId();
        mapRequestToEntity(request, rule);
        mapper.updateById(rule);

        log.info("更新 LLM 计费规则: id={}", id);

        // 刷新缓存（可能涉及两个 Provider）
        cacheService.evict(oldProviderId);
        if (!oldProviderId.equals(rule.getLlmProviderId())) {
            cacheService.evict(rule.getLlmProviderId());
        }

        return LlmBillingRuleResponse.fromEntity(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        LlmBillingRule rule = mapper.selectById(id);
        if (rule == null || rule.getDeleted() != 0) {
            throw new BusinessException("LLM 计费规则不存在: " + id);
        }

        mapper.deleteById(id);

        log.info("删除 LLM 计费规则: id={}", id);

        // 刷新缓存
        cacheService.evict(rule.getLlmProviderId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(String id, Boolean enabled) {
        LlmBillingRule rule = mapper.selectById(id);
        if (rule == null || rule.getDeleted() != 0) {
            throw new BusinessException("LLM 计费规则不存在: " + id);
        }

        rule.setEnabled(enabled);
        mapper.updateById(rule);

        log.info("切换 LLM 计费规则启用状态: id={}, enabled={}", id, enabled);

        // 刷新缓存
        cacheService.evict(rule.getLlmProviderId());
    }

    @Override
    public void refreshCache(String llmProviderId) {
        cacheService.refresh(llmProviderId);
        log.info("强制刷新 LLM 计费规则缓存: llmProviderId={}", llmProviderId);
    }

    @Override
    public void refreshAllCaches() {
        cacheService.refreshAll();
        log.info("强制刷新所有 LLM 计费规则缓存");
    }

    /**
     * 映射请求到实体
     */
    private void mapRequestToEntity(LlmBillingRuleRequest request, LlmBillingRule entity) {
        if (request.getLlmProviderId() != null) {
            entity.setLlmProviderId(request.getLlmProviderId());
        }
        if (request.getInputPrice() != null) {
            entity.setInputPrice(request.getInputPrice());
        } else if (entity.getInputPrice() == null) {
            entity.setInputPrice(BigDecimal.ZERO);
        }
        if (request.getOutputPrice() != null) {
            entity.setOutputPrice(request.getOutputPrice());
        } else if (entity.getOutputPrice() == null) {
            entity.setOutputPrice(BigDecimal.ZERO);
        }
        if (request.getEffectiveFrom() != null) {
            entity.setEffectiveFrom(request.getEffectiveFrom());
        }
        if (request.getEffectiveTo() != null) {
            entity.setEffectiveTo(request.getEffectiveTo());
        }
        if (request.getRateLimitRpm() != null) {
            entity.setRateLimitRpm(request.getRateLimitRpm());
        } else if (entity.getRateLimitRpm() == null) {
            entity.setRateLimitRpm(60);
        }
        if (request.getRateLimitTpm() != null) {
            entity.setRateLimitTpm(request.getRateLimitTpm());
        } else if (entity.getRateLimitTpm() == null) {
            entity.setRateLimitTpm(1000000);
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
}
