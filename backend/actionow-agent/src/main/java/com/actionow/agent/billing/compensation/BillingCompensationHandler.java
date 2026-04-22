package com.actionow.agent.billing.compensation;

import com.actionow.agent.billing.entity.AgentBillingSession;
import com.actionow.agent.billing.mapper.AgentBillingSessionMapper;
import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.feign.WalletFeignClient;
import com.actionow.agent.feign.dto.ConfirmConsumeRequest;
import com.actionow.agent.feign.dto.UnfreezeRequest;
import com.actionow.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 计费补偿处理器
 * 处理计费会话结算失败的补偿逻辑
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingCompensationHandler {

    private final AgentBillingSessionMapper billingSessionMapper;
    private final WalletFeignClient walletFeignClient;
    private final AgentRuntimeConfigService agentRuntimeConfig;

    /**
     * 业务类型：Agent 会话
     */
    private static final String BUSINESS_TYPE_AGENT_SESSION = "AGENT_SESSION";

    /**
     * 最大重试次数（默认值，实际从动态配置读取）
     */
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    /**
     * settle_error 字段中嵌入重试次数的前缀格式
     */
    private static final java.util.regex.Pattern RETRY_COUNT_PATTERN =
            java.util.regex.Pattern.compile("^\\[retry:(\\d+)\\]");

    /**
     * 处理失败的计费会话
     * 尝试重新结算或释放冻结
     *
     * @param limit 处理数量限制
     * @return 处理成功的数量
     */
    public int handleFailedSessions(int limit) {
        List<AgentBillingSession> failedSessions = billingSessionMapper.selectFailedSessions(limit);

        int successCount = 0;
        for (AgentBillingSession session : failedSessions) {
            try {
                if (handleFailedSession(session)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("处理失败会话异常: sessionId={}", session.getId(), e);
            }
        }

        log.info("处理失败计费会话完成: total={}, success={}", failedSessions.size(), successCount);
        return successCount;
    }

    /**
     * 处理单个失败的计费会话
     *
     * @param session 计费会话
     * @return 是否处理成功
     */
    private boolean handleFailedSession(AgentBillingSession session) {
        // 判断是否应该重试结算还是释放冻结
        LocalDateTime createdAt = session.getCreatedAt();
        LocalDateTime now = LocalDateTime.now();

        // 如果会话创建超过补偿超时时间，直接释放冻结
        int timeoutHours = agentRuntimeConfig.getBillingCompensationTimeoutHours();
        if (createdAt.plusHours(timeoutHours).isBefore(now)) {
            return releaseFreeze(session);
        }

        // 否则尝试重新结算
        return retrySettle(session);
    }

    /**
     * 重试结算
     */
    private boolean retrySettle(AgentBillingSession session) {
        // 检查重试次数，超限后停止自动重试，等待人工介入
        int retryCount = parseRetryCount(session.getSettleError());
        int maxRetryCount = agentRuntimeConfig.getBillingMaxRetryCount();
        if (retryCount >= maxRetryCount) {
            log.warn("已达最大重试次数 {}，停止自动重试，等待人工处理: sessionId={}", maxRetryCount, session.getId());
            billingSessionMapper.updateSettleError(session.getId(),
                    String.format("[retry:%d] 已达最大重试次数，等待人工处理", retryCount),
                    LocalDateTime.now());
            return false;
        }

        log.info("重试结算计费会话: sessionId={}, retryCount={}", session.getId(), retryCount + 1);

        // 封顶：totalCost 不得超过 frozenAmount（与主结算路径保持一致）
        long safeAmount = Math.min(session.getTotalCost(), session.getFrozenAmount());

        try {
            // 调用钱包服务确认消费
            ConfirmConsumeRequest request = ConfirmConsumeRequest.builder()
                    .workspaceId(session.getWorkspaceId())
                    .operatorId(session.getUserId())
                    .businessId(session.getConversationId())
                    .businessType(BUSINESS_TYPE_AGENT_SESSION)
                    .actualAmount(safeAmount)
                    .remark("Agent 会话补偿结算")
                    .build();

            Result<Void> result = walletFeignClient.confirmConsume(
                    session.getWorkspaceId(), request);

            if (result.isSuccess()) {
                billingSessionMapper.updateStatusToSettled(session.getId(), safeAmount, LocalDateTime.now());
                log.info("补偿结算成功: sessionId={}, safeAmount={}", session.getId(), safeAmount);
                return true;
            } else {
                billingSessionMapper.updateSettleError(session.getId(),
                        String.format("[retry:%d] 补偿结算失败: %s", retryCount + 1, result.getMessage()),
                        LocalDateTime.now());
                log.warn("补偿结算失败: sessionId={}, message={}", session.getId(), result.getMessage());
                return false;
            }
        } catch (Exception e) {
            billingSessionMapper.updateSettleError(session.getId(),
                    String.format("[retry:%d] 补偿结算异常: %s", retryCount + 1, e.getMessage()),
                    LocalDateTime.now());
            log.error("补偿结算异常: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 释放冻结金额
     */
    private boolean releaseFreeze(AgentBillingSession session) {
        log.info("释放冻结金额: sessionId={}", session.getId());

        try {
            // 如果有实际消费，先确认消费部分（封顶：不超过冻结额，防止余额下溢）
            if (session.getTotalCost() > 0) {
                long safeAmount = Math.min(session.getTotalCost(), session.getFrozenAmount());
                ConfirmConsumeRequest confirmRequest = ConfirmConsumeRequest.builder()
                        .workspaceId(session.getWorkspaceId())
                        .operatorId(session.getUserId())
                        .businessId(session.getConversationId())
                        .businessType(BUSINESS_TYPE_AGENT_SESSION)
                        .actualAmount(safeAmount)
                        .remark("Agent 会话补偿结算（部分消费）")
                        .build();

                Result<Void> confirmResult = walletFeignClient.confirmConsume(
                        session.getWorkspaceId(), confirmRequest);

                if (confirmResult.isSuccess()) {
                    billingSessionMapper.updateStatusToSettled(session.getId(), safeAmount, LocalDateTime.now());
                    log.info("补偿结算成功（部分消费）: sessionId={}, safeAmount={}",
                            session.getId(), safeAmount);
                    return true;
                }
            }

            // 无实际消费，直接解冻
            UnfreezeRequest unfreezeRequest = UnfreezeRequest.builder()
                    .workspaceId(session.getWorkspaceId())
                    .operatorId(session.getUserId())
                    .businessId(session.getConversationId())
                    .businessType(BUSINESS_TYPE_AGENT_SESSION)
                    .remark("Agent 会话超时解冻")
                    .build();

            Result<Void> unfreezeResult = walletFeignClient.unfreeze(
                    session.getWorkspaceId(), unfreezeRequest);

            if (unfreezeResult.isSuccess()) {
                billingSessionMapper.updateStatusToSettled(session.getId(), 0L, LocalDateTime.now());
                log.info("解冻成功: sessionId={}", session.getId());
                return true;
            } else {
                billingSessionMapper.updateSettleError(session.getId(),
                        "解冻失败: " + unfreezeResult.getMessage(), LocalDateTime.now());
                log.warn("解冻失败: sessionId={}, message={}", session.getId(), unfreezeResult.getMessage());
                return false;
            }
        } catch (Exception e) {
            billingSessionMapper.updateSettleError(session.getId(),
                    "释放冻结异常: " + e.getMessage(), LocalDateTime.now());
            log.error("释放冻结异常: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 从 settle_error 字段中解析已重试次数。
     * 格式约定：settle_error 以 "[retry:N]" 开头，其中 N 为已重试次数。
     */
    private int parseRetryCount(String settleError) {
        if (settleError == null || settleError.isEmpty()) {
            return 0;
        }
        java.util.regex.Matcher matcher = RETRY_COUNT_PATTERN.matcher(settleError);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 处理空闲会话（超时未活动的会话）
     *
     * @param idleThreshold 空闲阈值时间
     * @param limit         处理数量限制
     * @return 处理成功的数量
     */
    public int handleIdleSessions(LocalDateTime idleThreshold, int limit) {
        List<AgentBillingSession> idleSessions = billingSessionMapper.selectIdleSessions(idleThreshold, limit);

        int successCount = 0;
        for (AgentBillingSession session : idleSessions) {
            try {
                // 更新状态为结算中
                int updated = billingSessionMapper.updateStatusToSettling(session.getId(), LocalDateTime.now());
                if (updated == 0) {
                    continue;
                }

                // 尝试结算
                if (retrySettle(session)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("处理空闲会话异常: sessionId={}", session.getId(), e);
                billingSessionMapper.updateStatusToFailed(session.getId(), e.getMessage(), LocalDateTime.now());
            }
        }

        log.info("处理空闲计费会话完成: total={}, success={}", idleSessions.size(), successCount);
        return successCount;
    }
}
