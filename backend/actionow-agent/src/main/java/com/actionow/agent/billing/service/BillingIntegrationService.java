package com.actionow.agent.billing.service;

import com.actionow.agent.billing.dto.BillingSessionResponse;
import com.actionow.agent.billing.dto.TokenUsageRecord;
import com.actionow.agent.billing.exception.InsufficientCreditsException;
import com.actionow.agent.billing.exception.InsufficientQuotaException;
import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.feign.WalletFeignClient;
import com.actionow.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 计费集成服务
 * 为 ActionowAgentRunner 提供计费集成的便捷方法
 *
 * 职责：
 * 1. 计费会话管理（启动、结算、取消）
 * 2. 成员配额管理（检查、占用、退还）
 * 3. Token 消费记录
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingIntegrationService {

    private final AgentBillingService billingService;
    private final AgentBillingCalculator billingCalculator;
    private final WalletFeignClient walletFeignClient;

    /**
     * 启动计费会话（会话开始时调用）
     *
     * 流程：
     * 1. 检查成员配额是否足够
     * 2. 启动计费会话（冻结积分）
     * 3. 占用成员配额
     *
     * @param workspaceId    工作空间 ID
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param agentType      Agent 类型
     * @return 计费会话信息，如果计费功能未启用则返回 null
     * @throws InsufficientCreditsException 如果积分不足
     * @throws InsufficientQuotaException 如果成员配额不足
     */
    public BillingSessionResponse startBilling(String workspaceId, String conversationId,
                                                String userId, String agentType) {
        // 1. 预检查成员配额（使用默认冻结金额）
        long estimatedAmount = billingCalculator.getDefaultFreezeAmount();
        if (!checkMemberQuota(workspaceId, userId, estimatedAmount)) {
            log.warn("成员配额不足: workspaceId={}, userId={}, estimatedAmount={}",
                    workspaceId, userId, estimatedAmount);
            throw new InsufficientQuotaException(userId, estimatedAmount);
        }

        BillingSessionResponse response = null;
        try {
            // 2. 启动计费会话（冻结积分）
            response = billingService.startBillingSession(workspaceId, conversationId, userId, agentType, null);

            if (response != null) {
                // 3. 占用成员配额
                long frozenAmount = response.getFrozenAmount();
                boolean quotaUsed = useMemberQuota(workspaceId, userId, frozenAmount);
                if (!quotaUsed) {
                    // 配额占用失败，尝试取消计费会话（解冻积分）
                    log.warn("成员配额占用失败，取消计费会话: workspaceId={}, userId={}, frozenAmount={}",
                            workspaceId, userId, frozenAmount);
                    try {
                        billingService.cancelBillingSession(conversationId, workspaceId, userId);
                    } catch (Exception cancelEx) {
                        // 取消失败只记录，不掩盖配额不足异常；
                        // 残留冻结由定时补偿任务（BillingSettlementScheduler）清理
                        log.error("取消计费会话失败，积分可能残留冻结，需人工核查: conversationId={}, error={}",
                                conversationId, cancelEx.getMessage());
                    }
                    throw new InsufficientQuotaException(userId, frozenAmount);
                }
                log.info("计费会话启动成功，已占用配额: conversationId={}, frozenAmount={}",
                        conversationId, frozenAmount);
            }
            return response;
        } catch (InsufficientQuotaException e) {
            throw e;
        } catch (Exception e) {
            // 检查是否是积分不足异常
            if (InsufficientCreditsException.isInsufficientCredits(e)) {
                log.warn("积分不足，无法启动计费会话: conversationId={}, error={}", conversationId, e.getMessage());
                throw new InsufficientCreditsException(e.getMessage());
            }
            log.error("启动计费会话失败: conversationId={}, error={}", conversationId, e.getMessage());
            // 其他计费失败不阻止会话进行，记录日志后继续
            return null;
        }
    }

    /**
     * 记录 Token 消费（每次 LLM 响应后调用）
     *
     * @param conversationId 会话 ID
     * @param messageId      消息 ID
     * @param tokenUsage     Token 使用统计（来自 AgentStreamEvent）
     */
    public void recordTokenUsage(String conversationId, String messageId,
                                  AgentStreamEvent.TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            log.warn("Token usage is null for conversationId={}, cannot record", conversationId);
            return;
        }

        try {
            int inputTokens = tokenUsage.getPromptTokens() != null
                    ? tokenUsage.getPromptTokens().intValue() : 0;
            int outputTokens = tokenUsage.getCompletionTokens() != null
                    ? tokenUsage.getCompletionTokens().intValue() : 0;
            int thoughtTokens = tokenUsage.getThoughtTokens() != null
                    ? tokenUsage.getThoughtTokens().intValue() : 0;
            int cachedTokens = tokenUsage.getCachedTokens() != null
                    ? tokenUsage.getCachedTokens().intValue() : 0;

            log.info("Recording token usage: conversationId={}, messageId={}, inputTokens={}, outputTokens={}, thoughtTokens={}, cachedTokens={}, totalTokens={}",
                    conversationId, messageId, inputTokens, outputTokens, thoughtTokens, cachedTokens,
                    tokenUsage.getTotalTokens() != null ? tokenUsage.getTotalTokens() : (inputTokens + outputTokens + thoughtTokens));

            if (inputTokens > 0 || outputTokens > 0 || thoughtTokens > 0) {
                TokenUsageRecord record = billingService.recordTokenUsage(
                        conversationId, messageId, inputTokens, outputTokens, thoughtTokens, cachedTokens);
                log.info("Token usage recorded: conversationId={}, inputTokens={}, outputTokens={}, thoughtTokens={}, cost={}",
                        conversationId, inputTokens, outputTokens, thoughtTokens, record.getCost());
            } else {
                log.warn("All token counts are 0, skipping token recording: conversationId={}",
                        conversationId);
            }
        } catch (InsufficientCreditsException e) {
            // 积分不足必须向上传播，让 Agent 执行器停止生成，避免"吞异常继续跑"导致真实超额消费无记录
            log.warn("积分不足，停止继续消费: conversationId={}, error={}", conversationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            // 检查是否是会话已结算的情况，这种情况下只需要警告而不是错误
            if (e.getMessage() != null && e.getMessage().contains("SETTLED")) {
                log.warn("计费会话已结算，跳过 Token 记录: conversationId={}", conversationId);
            } else {
                log.error("记录 Token 消费失败: conversationId={}, error={}", conversationId, e.getMessage());
            }
            // 记录失败不阻止会话进行
        }
    }

    /**
     * 记录 AI 工具消费
     *
     * @param conversationId 会话 ID
     * @param toolName       工具名称
     * @param cost           消费积分
     */
    public void recordAiToolUsage(String conversationId, String toolName, long cost) {
        try {
            billingService.recordAiToolUsage(conversationId, toolName, cost);
            log.debug("记录 AI 工具消费: conversationId={}, toolName={}, cost={}",
                    conversationId, toolName, cost);
        } catch (InsufficientCreditsException e) {
            // 与 Token 计费一致：积分不足必须向上传播以停止 Agent 继续调用工具
            log.warn("积分不足，停止继续调用工具: conversationId={}, toolName={}, error={}",
                    conversationId, toolName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("记录 AI 工具消费失败: conversationId={}, error={}", conversationId, e.getMessage());
        }
    }

    /**
     * 结算计费会话（会话结束时调用）
     *
     * @param conversationId 会话 ID
     * @param workspaceId    工作空间 ID
     * @param userId         用户 ID
     */
    public void settleBilling(String conversationId, String workspaceId, String userId) {
        try {
            BillingSessionResponse response = billingService.settleBillingSession(
                    conversationId, workspaceId, userId);
            log.info("结算计费会话成功: conversationId={}, totalCost={}",
                    conversationId, response.getTotalCost());
        } catch (Exception e) {
            log.error("结算计费会话失败: conversationId={}, error={}", conversationId, e.getMessage());
            // 结算失败会由定时任务重试
        }
    }

    /**
     * 取消计费会话（会话取消时调用）
     *
     * 流程：
     * 1. 取消计费会话（解冻积分）
     * 2. 退还成员配额
     *
     * @param conversationId 会话 ID
     * @param workspaceId    工作空间 ID
     * @param userId         用户 ID
     */
    public void cancelBilling(String conversationId, String workspaceId, String userId) {
        try {
            // 1. 获取计费会话信息（用于获取冻结金额）
            BillingSessionResponse session = billingService.getBillingSession(conversationId);
            long frozenAmount = session != null ? session.getFrozenAmount() : 0;

            // 2. 取消计费会话
            billingService.cancelBillingSession(conversationId, workspaceId, userId);
            log.info("取消计费会话成功: conversationId={}", conversationId);

            // 3. 退还成员配额
            if (frozenAmount > 0) {
                refundMemberQuota(workspaceId, userId, frozenAmount);
                log.info("已退还成员配额: workspaceId={}, userId={}, amount={}",
                        workspaceId, userId, frozenAmount);
            }
        } catch (Exception e) {
            log.error("取消计费会话失败: conversationId={}, error={}", conversationId, e.getMessage());
        }
    }

    /**
     * 获取计费会话状态
     *
     * @param conversationId 会话 ID
     * @return 计费会话信息
     */
    public BillingSessionResponse getBillingSession(String conversationId) {
        try {
            return billingService.getBillingSession(conversationId);
        } catch (Exception e) {
            log.error("获取计费会话失败: conversationId={}, error={}", conversationId, e.getMessage());
            return null;
        }
    }

    /**
     * 检查计费会话是否存在
     *
     * @param conversationId 会话 ID
     * @return 是否存在
     */
    public boolean hasBillingSession(String conversationId) {
        return getBillingSession(conversationId) != null;
    }

    // ==================== 成员配额辅助方法 ====================

    /**
     * 检查成员配额是否足够
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param amount      预计使用的积分数量
     * @return 配额是否足够
     */
    private boolean checkMemberQuota(String workspaceId, String userId, long amount) {
        try {
            Result<Boolean> result = walletFeignClient.checkQuota(workspaceId, userId, amount);
            if (result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
            // 预检查返回非成功：fail-closed，拒绝本次请求。
            // useQuota() 才是最终扣减门卫，此处预检只做快速拒绝；
            // 保守拒绝比默认放行更安全（后续 freeze() 也会因服务异常失败）。
            log.warn("配额预检查返回失败，拒绝本次请求: workspaceId={}, userId={}, message={}",
                    workspaceId, userId, result.getMessage());
            return false;
        } catch (Exception e) {
            // 服务不可达时同样 fail-closed
            log.warn("配额预检查服务调用失败，拒绝本次请求: workspaceId={}, userId={}, error={}",
                    workspaceId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * 使用成员配额
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param amount      使用的积分数量
     * @return 是否成功
     */
    private boolean useMemberQuota(String workspaceId, String userId, long amount) {
        try {
            Result<Boolean> result = walletFeignClient.useQuota(workspaceId, userId, amount);
            if (result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
            log.warn("配额使用返回失败: workspaceId={}, userId={}, amount={}, message={}",
                    workspaceId, userId, amount, result.getMessage());
            return false;
        } catch (Exception e) {
            log.error("配额使用服务调用失败: workspaceId={}, userId={}, amount={}, error={}",
                    workspaceId, userId, amount, e.getMessage());
            return false;
        }
    }

    /**
     * 退还成员配额
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param amount      退还的积分数量
     */
    private void refundMemberQuota(String workspaceId, String userId, long amount) {
        try {
            Result<Boolean> result = walletFeignClient.refundQuota(workspaceId, userId, amount);
            if (!result.isSuccess()) {
                log.warn("配额退还返回失败: workspaceId={}, userId={}, amount={}, message={}",
                        workspaceId, userId, amount, result.getMessage());
            }
        } catch (Exception e) {
            log.error("配额退还服务调用失败: workspaceId={}, userId={}, amount={}, error={}",
                    workspaceId, userId, amount, e.getMessage());
            // 退还失败不抛出异常，后续可通过补偿任务处理
        }
    }
}
