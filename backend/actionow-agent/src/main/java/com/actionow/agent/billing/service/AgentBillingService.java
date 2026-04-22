package com.actionow.agent.billing.service;

import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.billing.dto.LlmBillingRuleResponse;
import com.actionow.agent.billing.dto.BillingSessionResponse;
import com.actionow.agent.billing.dto.TokenUsageRecord;
import com.actionow.agent.billing.entity.AgentBillingSession;
import com.actionow.agent.billing.mapper.AgentBillingSessionMapper;
import com.actionow.agent.config.entity.AgentConfigEntity;
import com.actionow.agent.config.service.AgentConfigService;
import com.actionow.agent.constant.BillingSessionStatus;
import com.actionow.agent.feign.AiFeignClient;
import com.actionow.agent.feign.WalletFeignClient;
import com.actionow.agent.feign.dto.ConfirmConsumeRequest;
import com.actionow.agent.feign.dto.FreezeRequest;
import com.actionow.agent.feign.dto.FreezeResponse;
import com.actionow.agent.feign.dto.LlmProviderResponse;
import com.actionow.agent.feign.dto.UnfreezeRequest;
import com.actionow.agent.billing.exception.InsufficientCreditsException;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.id.UuidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 计费服务
 * 管理计费会话的生命周期：创建、记录消费、结算
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentBillingService {

    private final AgentBillingSessionMapper billingSessionMapper;
    private final AgentBillingCalculator billingCalculator;
    private final AgentRuntimeConfigService agentRuntimeConfig;
    private final AgentConfigService agentConfigService;
    private final AiFeignClient aiFeignClient;
    private final LlmBillingRuleService billingRuleService;
    private final WalletFeignClient walletFeignClient;

    /**
     * 业务类型：Agent 会话
     */
    private static final String BUSINESS_TYPE_AGENT_SESSION = "AGENT_SESSION";

    /**
     * 开始计费会话
     * 冻结积分并创建计费会话记录
     *
     * @param workspaceId    工作空间 ID
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param agentType      Agent 类型
     * @param modelId        指定模型 ID（可选）
     * @return 计费会话信息
     */
    @Transactional
    public BillingSessionResponse startBillingSession(String workspaceId, String conversationId,
                                                       String userId, String agentType, String modelId) {
        // 0. 检查是否已存在 ACTIVE 计费会话（每条消息需要新的计费会话）
        AgentBillingSession existingSession = billingSessionMapper.selectActiveByConversationId(conversationId);
        if (existingSession != null) {
            long ageMs = java.time.Duration.between(
                    existingSession.getLastActivityAt(), LocalDateTime.now()
            ).toMillis();
            if (ageMs < 5000) {
                // 5秒内的复用请求，可能是重试，直接复用
                log.info("复用近期 ACTIVE 计费会话: conversationId={}, sessionId={}, ageMs={}",
                        conversationId, existingSession.getId(), ageMs);
                return BillingSessionResponse.fromEntity(existingSession);
            }
            // 旧会话超时未结算，先尝试结算
            try {
                log.info("结算过期 ACTIVE 计费会话: sessionId={}, ageMs={}",
                        existingSession.getId(), ageMs);
                settleBillingSession(conversationId, workspaceId, userId);
            } catch (Exception e) {
                log.warn("结算过期会话失败，继续创建新会话: {}", e.getMessage());
            }
        }
        // 注意：如果没有 ACTIVE 会话（可能之前的已结算），将创建新会话

        // 1. 获取 LLM 配置
        String llmProviderId = modelId;
        if (llmProviderId == null) {
            AgentConfigEntity agentConfig = agentConfigService.getEntityByAgentType(agentType);
            if (agentConfig == null || agentConfig.getLlmProviderId() == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "未找到 Agent 类型的 LLM 配置: " + agentType);
            }
            llmProviderId = agentConfig.getLlmProviderId();
        }

        // 从 actionow-ai 获取 LLM Provider 详情
        Result<LlmProviderResponse> providerResult = aiFeignClient.getLlmProviderById(llmProviderId);
        if (!providerResult.isSuccess() || providerResult.getData() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "未找到 LLM Provider: " + llmProviderId);
        }
        LlmProviderResponse llmProvider = providerResult.getData();

        // 2. 获取计费规则并创建定价快照
        LlmBillingRuleResponse billingRule = billingRuleService.getEffectiveRule(llmProviderId).orElse(null);

        Map<String, Object> pricingSnapshot = createPricingSnapshot(llmProvider, billingRule);

        // 3. 计算预冻结金额
        long freezeAmount = billingCalculator.getDefaultFreezeAmount();

        // 4. 调用钱包服务冻结积分
        FreezeRequest freezeRequest = FreezeRequest.builder()
                .workspaceId(workspaceId)
                .operatorId(userId)
                .amount(freezeAmount)
                .businessType(BUSINESS_TYPE_AGENT_SESSION)
                .businessId(conversationId)
                .remark("Agent 会话预冻结")
                .build();

        Result<FreezeResponse> freezeResult = walletFeignClient.freeze(workspaceId, freezeRequest);
        if (!freezeResult.isSuccess()) {
            throw new BusinessException(ResultCode.FAIL,
                    "冻结积分失败: " + freezeResult.getMessage());
        }

        String transactionId = freezeResult.getData().getTransactionId();

        // 5. 创建计费会话记录
        AgentBillingSession session = new AgentBillingSession();
        session.setId(UuidGenerator.generateUuidV7());
        session.setWorkspaceId(workspaceId);
        session.setConversationId(conversationId);
        session.setUserId(userId);
        session.setModelProvider(llmProvider.getProvider());
        session.setModelId(llmProvider.getModelId());
        session.setModelName(llmProvider.getModelName());
        session.setTransactionId(transactionId);
        session.setFrozenAmount(freezeAmount);
        session.setTotalInputTokens(0L);
        session.setTotalOutputTokens(0L);
        session.setTotalThoughtTokens(0L);
        session.setTotalCachedTokens(0L);
        session.setLlmCost(0L);
        session.setAiToolCalls(0);
        session.setAiToolCost(0L);
        session.setTotalCost(0L);
        session.setPricingSnapshot(pricingSnapshot);
        session.setStatus(BillingSessionStatus.ACTIVE.getCode());
        session.setLastActivityAt(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        billingSessionMapper.insert(session);

        log.info("创建计费会话: sessionId={}, conversationId={}, freezeAmount={}",
                session.getId(), conversationId, freezeAmount);

        return BillingSessionResponse.fromEntity(session);
    }

    /**
     * 记录 Token 消费
     *
     * @param conversationId 会话 ID
     * @param messageId      消息 ID
     * @param inputTokens    输入 Token 数
     * @param outputTokens   输出 Token 数
     * @param thoughtTokens  思考 Token 数
     * @param cachedTokens   缓存 Token 数
     * @return Token 使用记录
     */
    @Transactional
    public TokenUsageRecord recordTokenUsage(String conversationId, String messageId,
                                              int inputTokens, int outputTokens,
                                              int thoughtTokens, int cachedTokens) {
        // 1. 查询 ACTIVE 或 SETTLING 状态的计费会话
        AgentBillingSession session = billingSessionMapper.selectByConversationId(conversationId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,
                    "计费会话不存在: conversationId=" + conversationId);
        }

        // 验证状态：允许 ACTIVE 和 SETTLING 状态记录消费（SETTLING 时仍可能有最后的 token 记录）
        if (!BillingSessionStatus.ACTIVE.getCode().equals(session.getStatus()) &&
                !BillingSessionStatus.SETTLING.getCode().equals(session.getStatus())) {
            // 如果当前会话已结算，尝试查找 ACTIVE 会话
            AgentBillingSession activeSession = billingSessionMapper.selectActiveByConversationId(conversationId);
            if (activeSession != null) {
                session = activeSession;
                log.info("使用 ACTIVE 计费会话替代已结算会话: sessionId={}", session.getId());
            } else {
                throw new BusinessException(ResultCode.FAIL,
                        "计费会话状态不正确，只有 ACTIVE 或 SETTLING 状态可记录消费: status=" + session.getStatus());
            }
        }

        // 2. 计算费用（使用定价快照，思考 Token 按输出 Token 价格计费）
        TokenUsageRecord record = billingCalculator.calculateAndRecord(
                session.getId(), messageId,
                session.getModelProvider(), session.getModelId(),
                session.getPricingSnapshot(),
                inputTokens, outputTokens, thoughtTokens);

        // 3. 无条件写入本次消费（LLM 已经真实生成了这些 Token，必须如实记账，
        // 否则 settlement 只能按旧的 totalCost 结算，造成系统性少收费）
        billingSessionMapper.updateTokenUsage(session.getId(),
                inputTokens, outputTokens, thoughtTokens, cachedTokens, record.getCost(),
                LocalDateTime.now());

        log.debug("记录 Token 消费: sessionId={}, inputTokens={}, outputTokens={}, thoughtTokens={}, cachedTokens={}, cost={}",
                session.getId(), inputTokens, outputTokens, thoughtTokens, cachedTokens, record.getCost());

        // 4. 超额后置检查：写入后读取 DB 真实 totalCost 判断是否已超冻结。
        // 写入前检查有 TOCTOU：A、B 都读到 totalCost=80 <= 100，都写入 20，结果变成 120；
        // 写入后检查基于 DB 真实聚合值，虽然仍有多个请求可能同时越线，但信号依然会触发，
        // 结算端会按 frozenAmount 封顶扣款，保证账务不下溢。
        checkOverageOrThrow(session.getId());

        // 5. 检查是否需要追加冻结
        checkAndAddFrozenAmount(session);

        return record;
    }

    /**
     * 记录 AI 工具消费
     *
     * @param conversationId 会话 ID
     * @param toolName       工具名称
     * @param cost           消费积分
     */
    @Transactional
    public void recordAiToolUsage(String conversationId, String toolName, long cost) {
        // 1. 查询计费会话（优先 ACTIVE）
        AgentBillingSession session = billingSessionMapper.selectByConversationId(conversationId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,
                    "计费会话不存在: conversationId=" + conversationId);
        }

        // 验证状态：允许 ACTIVE 和 SETTLING 状态记录消费
        if (!BillingSessionStatus.ACTIVE.getCode().equals(session.getStatus()) &&
                !BillingSessionStatus.SETTLING.getCode().equals(session.getStatus())) {
            // 如果当前会话已结算，尝试查找 ACTIVE 会话
            AgentBillingSession activeSession = billingSessionMapper.selectActiveByConversationId(conversationId);
            if (activeSession != null) {
                session = activeSession;
                log.info("使用 ACTIVE 计费会话替代已结算会话: sessionId={}", session.getId());
            } else {
                throw new BusinessException(ResultCode.FAIL,
                        "计费会话状态不正确，只有 ACTIVE 或 SETTLING 状态可记录消费: status=" + session.getStatus());
            }
        }

        // 2. 无条件写入本次工具消费（工具已真实调用，必须记账）
        billingSessionMapper.updateAiToolUsage(session.getId(), cost, LocalDateTime.now());

        log.debug("记录 AI 工具消费: sessionId={}, toolName={}, cost={}",
                session.getId(), toolName, cost);

        // 3. 超额后置检查：基于 DB 真实 totalCost 判断是否需要触发停止信号
        checkOverageOrThrow(session.getId());

        // 4. 检查是否需要追加冻结
        checkAndAddFrozenAmount(session);
    }

    /**
     * 结算计费会话
     *
     * @param conversationId 会话 ID
     * @param workspaceId    工作空间 ID
     * @param operatorId     操作人 ID
     * @return 结算后的会话信息
     */
    @Transactional
    public BillingSessionResponse settleBillingSession(String conversationId,
                                                        String workspaceId, String operatorId) {
        // 1. 查询计费会话
        AgentBillingSession session = billingSessionMapper.selectByConversationId(conversationId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,
                    "计费会话不存在: conversationId=" + conversationId);
        }

        if (BillingSessionStatus.SETTLED.getCode().equals(session.getStatus())) {
            log.warn("计费会话已结算: sessionId={}", session.getId());
            return BillingSessionResponse.fromEntity(session);
        }

        // 2. 更新状态为结算中
        int updated = billingSessionMapper.updateStatusToSettling(session.getId(), LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException(ResultCode.FAIL, "更新会话状态失败");
        }

        // 重新从 DB 读取最新数据（SETTLING 状态后不会再有并发 token 写入，但冻结金额可能已被追加）
        session = billingSessionMapper.selectById(session.getId());

        try {
            // 3. 计算安全的消费金额：totalCost 不得超过 frozenAmount
            // 如果因追加冻结失败导致 totalCost > frozenAmount，以 frozenAmount 为上限，
            // 差额记录为对账差异（不能让 confirmConsume 写入超出冻结的金额，否则 frozen 字段会下溢）
            long safeConsumeAmount = session.getTotalCost();
            if (safeConsumeAmount > session.getFrozenAmount()) {
                log.error("结算对账差异：totalCost({}) > frozenAmount({})，" +
                        "可能因追加冻结失败导致，按冻结额封顶结算，差额 {} 积分需人工核查: sessionId={}, conversationId={}",
                        safeConsumeAmount, session.getFrozenAmount(),
                        safeConsumeAmount - session.getFrozenAmount(),
                        session.getId(), conversationId);
                safeConsumeAmount = session.getFrozenAmount();
            }

            // 4. 调用钱包服务确认消费
            ConfirmConsumeRequest confirmRequest = ConfirmConsumeRequest.builder()
                    .workspaceId(workspaceId)
                    .operatorId(operatorId)
                    .businessId(conversationId)
                    .businessType(BUSINESS_TYPE_AGENT_SESSION)
                    .actualAmount(safeConsumeAmount)
                    .remark("Agent 会话结算")
                    .build();

            Result<Void> confirmResult = walletFeignClient.confirmConsume(workspaceId, confirmRequest);
            if (!confirmResult.isSuccess()) {
                throw new BusinessException(ResultCode.FAIL,
                        "确认消费失败: " + confirmResult.getMessage());
            }

            // 5. 更新状态为已结算（以实际扣款的安全金额为准，非原始 totalCost）
            billingSessionMapper.updateStatusToSettled(session.getId(), safeConsumeAmount, LocalDateTime.now());

            log.info("结算计费会话: sessionId={}, totalCost={}", session.getId(), session.getTotalCost());

            // 重新查询返回最新状态
            session = billingSessionMapper.selectByConversationId(conversationId);
            return BillingSessionResponse.fromEntity(session);

        } catch (Exception e) {
            // 5. 结算失败，更新状态
            billingSessionMapper.updateStatusToFailed(session.getId(), e.getMessage(), LocalDateTime.now());
            log.error("结算计费会话失败: sessionId={}", session.getId(), e);
            throw e;
        }
    }

    /**
     * 取消计费会话（解冻积分）
     *
     * @param conversationId 会话 ID
     * @param workspaceId    工作空间 ID
     * @param operatorId     操作人 ID
     */
    @Transactional
    public void cancelBillingSession(String conversationId, String workspaceId, String operatorId) {
        // 1. 查询计费会话
        AgentBillingSession session = billingSessionMapper.selectByConversationId(conversationId);
        if (session == null) {
            log.warn("计费会话不存在: conversationId={}", conversationId);
            return;
        }

        if (!BillingSessionStatus.ACTIVE.getCode().equals(session.getStatus())) {
            log.warn("计费会话状态不是活跃中，无法取消: status={}", session.getStatus());
            return;
        }

        // 2. 调用钱包服务解冻积分
        UnfreezeRequest unfreezeRequest = UnfreezeRequest.builder()
                .workspaceId(workspaceId)
                .operatorId(operatorId)
                .businessId(conversationId)
                .businessType(BUSINESS_TYPE_AGENT_SESSION)
                .remark("Agent 会话取消")
                .build();

        Result<Void> unfreezeResult = walletFeignClient.unfreeze(workspaceId, unfreezeRequest);
        if (!unfreezeResult.isSuccess()) {
            String errorMsg = "解冻积分失败: " + unfreezeResult.getMessage();
            log.error("解冻积分失败，会话标记 FAILED 待补偿处理: conversationId={}, message={}",
                    conversationId, unfreezeResult.getMessage());
            billingSessionMapper.updateStatusToFailed(session.getId(), errorMsg, LocalDateTime.now());
            return;
        }

        // 3. 更新状态为已结算（实际消费为 0）
        billingSessionMapper.updateStatusToSettled(session.getId(), 0L, LocalDateTime.now());

        log.info("取消计费会话: sessionId={}", session.getId());
    }

    /**
     * 获取计费会话信息
     */
    public BillingSessionResponse getBillingSession(String conversationId) {
        AgentBillingSession session = billingSessionMapper.selectByConversationId(conversationId);
        return session != null ? BillingSessionResponse.fromEntity(session) : null;
    }

    /**
     * 获取用户的计费会话列表
     */
    public List<BillingSessionResponse> getUserBillingSessions(String userId, int limit) {
        return billingSessionMapper.selectByUserId(userId, limit).stream()
                .map(BillingSessionResponse::fromEntity)
                .toList();
    }

    /**
     * 分页获取所有计费会话
     */
    public PageResult<BillingSessionResponse> getAllBillingSessions(int page, int size) {
        Page<AgentBillingSession> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AgentBillingSession> wrapper = new LambdaQueryWrapper<AgentBillingSession>()
                .orderByDesc(AgentBillingSession::getCreatedAt);

        Page<AgentBillingSession> resultPage = billingSessionMapper.selectPage(pageParam, wrapper);

        List<BillingSessionResponse> records = resultPage.getRecords().stream()
                .map(BillingSessionResponse::fromEntity)
                .toList();

        return PageResult.of((long) page, (long) size, resultPage.getTotal(), records);
    }

    /**
     * 获取空闲的计费会话（用于定时结算）
     */
    public List<AgentBillingSession> getIdleSessions(LocalDateTime idleThreshold, int limit) {
        return billingSessionMapper.selectIdleSessions(idleThreshold, limit);
    }

    /**
     * 获取失败的计费会话（用于补偿重试）
     */
    public List<AgentBillingSession> getFailedSessions(int limit) {
        return billingSessionMapper.selectFailedSessions(limit);
    }

    /**
     * 创建定价快照
     */
    private Map<String, Object> createPricingSnapshot(LlmProviderResponse llmProvider,
                                                       LlmBillingRuleResponse billingRule) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("modelProvider", llmProvider.getProvider());
        snapshot.put("modelId", llmProvider.getModelId());
        snapshot.put("modelName", llmProvider.getModelName());

        if (billingRule != null) {
            snapshot.put("inputPrice", billingRule.getInputPrice());
            snapshot.put("outputPrice", billingRule.getOutputPrice());
            snapshot.put("ruleId", billingRule.getId());
        } else {
            // 使用默认价格
            snapshot.put("inputPrice", "0.5");
            snapshot.put("outputPrice", "1.5");
        }

        snapshot.put("snapshotAt", LocalDateTime.now().toString());
        return snapshot;
    }

    /**
     * 超额后置信号：本次消费已落库，读取 DB 真实 totalCost 判断是否已越过冻结额。
     * 设计要点：
     *  - 本方法不阻止本次写入（本次写入已完成且必须保留，否则 settlement 少收费）。
     *  - 本方法的作用是在检测到越线时抛出 {@link InsufficientCreditsException}，
     *    由上层 {@link BillingIntegrationService} 向 Agent 执行器传播 STOP 信号，
     *    停止继续生成/调用工具；后续结算会按 frozenAmount 封顶扣款。
     *  - 并发场景下多个请求可能都越线，但每个调用都会看到 DB 真实值并触发一次信号，
     *    调用方只需保证幂等处理 stop 信号即可。
     *
     * @param sessionId 会话 ID
     * @throws InsufficientCreditsException 若 totalCost 已超出 frozenAmount
     */
    private void checkOverageOrThrow(String sessionId) {
        AgentBillingSession latest = billingSessionMapper.selectById(sessionId);
        if (latest == null) {
            return;
        }
        long frozenAmount = latest.getFrozenAmount();
        // 冻结额为 0 视为不启用超额保护（未初始化场景或特殊测试路径）
        if (frozenAmount <= 0) {
            return;
        }
        if (latest.getTotalCost() > frozenAmount) {
            log.warn("积分余额不足，totalCost({}) > frozenAmount({})，发送停止信号: sessionId={}",
                    latest.getTotalCost(), frozenAmount, sessionId);
            throw new InsufficientCreditsException("积分余额不足，请充值后重试");
        }
    }

    /**
     * 检查是否需要追加冻结金额
     * 从 DB 重新读取最新数据，避免使用内存中的过期值导致竞态条件。
     * 注意：调用前 updateTokenUsage/updateAiToolUsage 已将本次费用写入 DB，
     * latest.getTotalCost() 已包含本次费用，无需再叠加。
     */
    private void checkAndAddFrozenAmount(AgentBillingSession session) {
        // 从 DB 重新读取最新数据（避免使用内存中的过期值）
        AgentBillingSession latest = billingSessionMapper.selectById(session.getId());
        if (latest == null || BillingSessionStatus.SETTLED.getCode().equals(latest.getStatus())) {
            return;
        }

        // 已消费金额（DB 中已包含本次刚写入的费用）
        long totalCost = latest.getTotalCost();
        // 如果已消费金额超过冻结金额的阈值比例，追加冻结
        long threshold = (long) (latest.getFrozenAmount() * agentRuntimeConfig.getBillingFreezeThresholdRatio());

        if (totalCost >= threshold) {
            long additionalFreeze = latest.getFrozenAmount(); // 追加同等金额

            FreezeRequest freezeRequest = FreezeRequest.builder()
                    .workspaceId(latest.getWorkspaceId())
                    .operatorId(latest.getUserId())
                    .amount(additionalFreeze)
                    .businessType(BUSINESS_TYPE_AGENT_SESSION)
                    .businessId(latest.getConversationId())
                    .remark("Agent 会话追加冻结")
                    .build();

            try {
                Result<FreezeResponse> result = walletFeignClient.freeze(
                        latest.getWorkspaceId(), freezeRequest);

                if (result.isSuccess()) {
                    billingSessionMapper.updateFrozenAmount(latest.getId(), additionalFreeze, LocalDateTime.now());
                    log.info("追加冻结成功: sessionId={}, additionalFreeze={}",
                            latest.getId(), additionalFreeze);
                }
            } catch (Exception e) {
                log.error("追加冻结失败（结算时 totalCost 可能超出 frozenAmount，届时将按冻结额封顶结算）: " +
                        "sessionId={}, additionalFreeze={}, error={}",
                        latest.getId(), additionalFreeze, e.getMessage());
                // 追加冻结失败不中断 token 记录，但 settleBillingSession 会对 consume 金额封顶
            }
        }
    }
}
