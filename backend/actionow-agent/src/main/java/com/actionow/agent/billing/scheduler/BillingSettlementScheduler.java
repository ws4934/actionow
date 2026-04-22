package com.actionow.agent.billing.scheduler;

import com.actionow.agent.billing.compensation.BillingCompensationHandler;
import com.actionow.agent.billing.entity.AgentBillingSession;
import com.actionow.agent.billing.service.AgentBillingService;
import com.actionow.agent.config.AgentBillingProperties;
import com.actionow.agent.config.AgentRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计费结算定时任务
 * 处理空闲会话自动结算和失败会话重试
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingSettlementScheduler {

    private final AgentBillingService billingService;
    private final AgentBillingProperties billingProperties;
    private final AgentRuntimeConfigService agentRuntimeConfig;
    private final BillingCompensationHandler compensationHandler;

    /**
     * 定时结算空闲会话
     * 每 5 分钟执行一次
     */
    @Scheduled(fixedDelayString = "${actionow.agent.billing.settle-interval-ms:300000}")
    public void settleIdleSessions() {
        log.debug("Starting idle session settlement task...");

        try {
            LocalDateTime idleThreshold = LocalDateTime.now().minusMinutes(agentRuntimeConfig.getBillingIdleTimeoutMinutes());
            List<AgentBillingSession> idleSessions = billingService.getIdleSessions(idleThreshold, agentRuntimeConfig.getBillingBatchSize());

            if (idleSessions.isEmpty()) {
                log.debug("No idle sessions to settle");
                return;
            }

            log.info("Found {} idle sessions to settle (threshold: {})", idleSessions.size(), idleThreshold);

            int successCount = 0;
            int failCount = 0;

            for (AgentBillingSession session : idleSessions) {
                try {
                    billingService.settleBillingSession(
                            session.getConversationId(),
                            session.getWorkspaceId(),
                            session.getUserId()
                    );
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to settle idle session: sessionId={}, conversationId={}, error={}",
                            session.getId(), session.getConversationId(), e.getMessage());
                }
            }

            log.info("Idle session settlement completed: success={}, failed={}", successCount, failCount);

        } catch (Exception e) {
            log.error("Idle session settlement task failed", e);
        }
    }

    /**
     * 定时重试失败的结算
     * 每 10 分钟执行一次
     * <p>
     * 使用 BillingCompensationHandler 而非直接调用 settleBillingSession，
     * 避免状态机限制（FAILED → SETTLING 转换可能被 Mapper 拒绝）并支持超时解冻逻辑。
     */
    @Scheduled(fixedDelayString = "${actionow.agent.billing.retry-interval-ms:600000}")
    public void retryFailedSessions() {
        log.debug("Starting failed session retry task...");

        try {
            int successCount = compensationHandler.handleFailedSessions(agentRuntimeConfig.getBillingBatchSize());
            if (successCount > 0) {
                log.info("Failed session retry completed: success={}", successCount);
            } else {
                log.debug("Failed session retry completed: no sessions processed");
            }
        } catch (Exception e) {
            log.error("Failed session retry task failed", e);
        }
    }
}
