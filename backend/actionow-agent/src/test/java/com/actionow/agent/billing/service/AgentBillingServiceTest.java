package com.actionow.agent.billing.service;

import com.actionow.agent.billing.dto.BillingSessionResponse;
import com.actionow.agent.billing.dto.LlmBillingRuleResponse;
import com.actionow.agent.billing.dto.TokenUsageRecord;
import com.actionow.agent.billing.entity.AgentBillingSession;
import com.actionow.agent.billing.exception.InsufficientCreditsException;
import com.actionow.agent.billing.mapper.AgentBillingSessionMapper;
import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.config.entity.AgentConfigEntity;
import com.actionow.agent.config.service.AgentConfigService;
import com.actionow.agent.feign.AiFeignClient;
import com.actionow.agent.feign.WalletFeignClient;
import com.actionow.agent.feign.dto.ConfirmConsumeRequest;
import com.actionow.agent.feign.dto.FreezeRequest;
import com.actionow.agent.feign.dto.FreezeResponse;
import com.actionow.agent.feign.dto.LlmProviderResponse;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentBillingService 单元测试
 *
 * @author Actionow
 */
@ExtendWith(MockitoExtension.class)
class AgentBillingServiceTest {

    @Mock
    private AgentBillingSessionMapper billingSessionMapper;

    @Mock
    private AgentBillingCalculator billingCalculator;

    @Mock
    private AgentRuntimeConfigService agentRuntimeConfig;

    @Mock
    private AgentConfigService agentConfigService;

    @Mock
    private AiFeignClient aiFeignClient;

    @Mock
    private LlmBillingRuleService billingRuleService;

    @Mock
    private WalletFeignClient walletFeignClient;

    @InjectMocks
    private AgentBillingService billingService;

    private static final String WORKSPACE_ID = "ws-123";
    private static final String CONVERSATION_ID = "conv-123";
    private static final String USER_ID = "user-123";
    private static final String MODEL_ID = "model-123";
    private static final String SESSION_ID = "session-123";

    private AgentBillingSession createActiveSession(long totalCost, long frozenAmount) {
        AgentBillingSession session = new AgentBillingSession();
        session.setId(SESSION_ID);
        session.setWorkspaceId(WORKSPACE_ID);
        session.setConversationId(CONVERSATION_ID);
        session.setUserId(USER_ID);
        session.setModelProvider("OPENAI");
        session.setModelId(MODEL_ID);
        session.setModelName("gpt-4");
        session.setTransactionId("txn-123");
        session.setFrozenAmount(frozenAmount);
        session.setTotalCost(totalCost);
        session.setTotalInputTokens(0L);
        session.setTotalOutputTokens(0L);
        session.setTotalThoughtTokens(0L);
        session.setTotalCachedTokens(0L);
        session.setLlmCost(0L);
        session.setAiToolCalls(0);
        session.setAiToolCost(0L);
        session.setStatus("ACTIVE");
        session.setPricingSnapshot(new HashMap<>(Map.of("inputPrice", "1.0", "outputPrice", "2.0")));
        session.setLastActivityAt(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    @Nested
    @DisplayName("startBillingSession 创建会话测试")
    class StartBillingSessionTests {

        @Test
        @DisplayName("正常创建计费会话")
        void startBillingSession_success() {
            when(billingSessionMapper.selectActiveByConversationId(CONVERSATION_ID)).thenReturn(null);

            AgentConfigEntity config = new AgentConfigEntity();
            config.setLlmProviderId(MODEL_ID);
            when(agentConfigService.getEntityByAgentType("CHAT")).thenReturn(config);

            LlmProviderResponse provider = new LlmProviderResponse();
            provider.setProvider("OPENAI");
            provider.setModelId(MODEL_ID);
            provider.setModelName("gpt-4");
            when(aiFeignClient.getLlmProviderById(MODEL_ID)).thenReturn(Result.success(provider));

            when(billingRuleService.getEffectiveRule(MODEL_ID)).thenReturn(Optional.empty());
            when(billingCalculator.getDefaultFreezeAmount()).thenReturn(100L);

            FreezeResponse freezeResponse = new FreezeResponse();
            freezeResponse.setTransactionId("txn-001");
            when(walletFeignClient.freeze(eq(WORKSPACE_ID), any(FreezeRequest.class)))
                    .thenReturn(Result.success(freezeResponse));

            BillingSessionResponse response = billingService.startBillingSession(
                    WORKSPACE_ID, CONVERSATION_ID, USER_ID, "CHAT", null);

            assertNotNull(response);
            ArgumentCaptor<AgentBillingSession> captor = ArgumentCaptor.forClass(AgentBillingSession.class);
            verify(billingSessionMapper).insert(captor.capture());
            assertNotNull(captor.getValue().getId());
        }

        @Test
        @DisplayName("复用 5 秒内的 ACTIVE 会话")
        void startBillingSession_reuseRecentSession() {
            AgentBillingSession existing = createActiveSession(0, 100);
            existing.setLastActivityAt(LocalDateTime.now().minusSeconds(2));
            when(billingSessionMapper.selectActiveByConversationId(CONVERSATION_ID)).thenReturn(existing);

            BillingSessionResponse response = billingService.startBillingSession(
                    WORKSPACE_ID, CONVERSATION_ID, USER_ID, "CHAT", null);

            assertNotNull(response);
            verify(billingSessionMapper, never()).insert(any(AgentBillingSession.class));
        }
    }

    @Nested
    @DisplayName("recordTokenUsage Token 消费记录测试")
    class RecordTokenUsageTests {

        @Test
        @DisplayName("正常记录 Token 消费")
        void recordTokenUsage_success() {
            AgentBillingSession session = createActiveSession(10, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            TokenUsageRecord record = TokenUsageRecord.builder()
                    .billingSessionId(SESSION_ID)
                    .messageId("msg-1")
                    .cost(5L)
                    .build();
            when(billingCalculator.calculateAndRecord(
                    eq(SESSION_ID), eq("msg-1"), eq("OPENAI"), eq(MODEL_ID),
                    any(), eq(100), eq(50), eq(20)))
                    .thenReturn(record);

            // 从 DB 重新读取的会话（用于 checkAndAddFrozenAmount）
            AgentBillingSession latestSession = createActiveSession(15, 100);
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestSession);

            TokenUsageRecord result = billingService.recordTokenUsage(
                    CONVERSATION_ID, "msg-1", 100, 50, 20, 10);

            assertNotNull(result);
            verify(billingSessionMapper).updateTokenUsage(
                    eq(SESSION_ID), eq(100), eq(50), eq(20), eq(10), eq(5L), any());
        }

        @Test
        @DisplayName("写入后检测到超额，抛出停止信号但保留本次写入")
        void recordTokenUsage_overageSignalAfterWrite() {
            // 会话当前 totalCost=95，frozenAmount=100，本次 cost=10 → 写入后 totalCost=105 超额
            AgentBillingSession session = createActiveSession(95, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            TokenUsageRecord record = TokenUsageRecord.builder()
                    .billingSessionId(SESSION_ID)
                    .messageId("msg-1")
                    .cost(10L)
                    .build();
            when(billingCalculator.calculateAndRecord(
                    eq(SESSION_ID), eq("msg-1"), eq("OPENAI"), eq(MODEL_ID),
                    any(), eq(100), eq(50), eq(20)))
                    .thenReturn(record);

            // 写入后 DB 状态：totalCost=105 > frozenAmount=100
            AgentBillingSession latestAfterWrite = createActiveSession(105, 100);
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestAfterWrite);

            // 必须抛出 InsufficientCreditsException 作为停止信号
            assertThrows(InsufficientCreditsException.class, () ->
                    billingService.recordTokenUsage(CONVERSATION_ID, "msg-1", 100, 50, 20, 10));

            // 关键断言：尽管抛异常，本次 updateTokenUsage 仍然执行了（LLM 已真实消费 Token，必须记账）
            verify(billingSessionMapper).updateTokenUsage(
                    eq(SESSION_ID), eq(100), eq(50), eq(20), eq(10), eq(10L), any());
        }

        @Test
        @DisplayName("冻结金额为 0 时不触发超额保护")
        void recordTokenUsage_zeroFrozenDoesNotBlock() {
            AgentBillingSession session = createActiveSession(50, 0);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            TokenUsageRecord record = TokenUsageRecord.builder()
                    .billingSessionId(SESSION_ID)
                    .messageId("msg-1")
                    .cost(5L)
                    .build();
            when(billingCalculator.calculateAndRecord(any(), any(), any(), any(), any(),
                    anyInt(), anyInt(), anyInt())).thenReturn(record);

            AgentBillingSession latestSession = createActiveSession(55, 0);
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestSession);

            TokenUsageRecord result = billingService.recordTokenUsage(
                    CONVERSATION_ID, "msg-1", 100, 50, 20, 10);
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("recordAiToolUsage AI 工具消费记录测试")
    class RecordAiToolUsageTests {

        @Test
        @DisplayName("正常记录 AI 工具消费")
        void recordAiToolUsage_success() {
            AgentBillingSession session = createActiveSession(10, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            // 写入后 DB 状态：totalCost=30，未超额
            AgentBillingSession latestAfterWrite = createActiveSession(30, 100);
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestAfterWrite);

            billingService.recordAiToolUsage(CONVERSATION_ID, "image-gen", 20L);

            verify(billingSessionMapper).updateAiToolUsage(eq(SESSION_ID), eq(20L), any());
        }

        @Test
        @DisplayName("写入后检测到超额，抛出停止信号但保留本次工具消费")
        void recordAiToolUsage_overageSignalAfterWrite() {
            AgentBillingSession session = createActiveSession(90, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            // 写入后 DB 状态：totalCost=140 > frozenAmount=100
            AgentBillingSession latestAfterWrite = createActiveSession(140, 100);
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestAfterWrite);

            assertThrows(InsufficientCreditsException.class, () ->
                    billingService.recordAiToolUsage(CONVERSATION_ID, "image-gen", 50L));

            // 即使抛出异常，本次工具消费已落库（工具已真实调用，必须记账）
            verify(billingSessionMapper).updateAiToolUsage(eq(SESSION_ID), eq(50L), any());
        }

        @Test
        @DisplayName("冻结金额为 0 时不触发超额保护")
        void recordAiToolUsage_zeroFrozenDoesNotBlock() {
            AgentBillingSession session = createActiveSession(50, 0);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            AgentBillingSession latestAfterWrite = createActiveSession(70, 0);
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestAfterWrite);

            assertDoesNotThrow(() ->
                    billingService.recordAiToolUsage(CONVERSATION_ID, "image-gen", 20L));

            verify(billingSessionMapper).updateAiToolUsage(eq(SESSION_ID), eq(20L), any());
        }
    }

    @Nested
    @DisplayName("settleBillingSession 结算测试")
    class SettleBillingSessionTests {

        @Test
        @DisplayName("正常结算")
        void settleBillingSession_success() {
            AgentBillingSession session = createActiveSession(80, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);
            when(billingSessionMapper.updateStatusToSettling(eq(SESSION_ID), any())).thenReturn(1);

            // 重新从 DB 读取最新数据
            AgentBillingSession latestSession = createActiveSession(80, 100);
            latestSession.setStatus("SETTLING");
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestSession);

            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            AgentBillingSession settledSession = createActiveSession(80, 100);
            settledSession.setStatus("SETTLED");
            settledSession.setSettledAmount(80L);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session, settledSession);

            BillingSessionResponse response = billingService.settleBillingSession(
                    CONVERSATION_ID, WORKSPACE_ID, USER_ID);

            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(80L), any());
        }

        @Test
        @DisplayName("已结算会话幂等处理")
        void settleBillingSession_alreadySettled() {
            AgentBillingSession session = createActiveSession(80, 100);
            session.setStatus("SETTLED");
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            BillingSessionResponse response = billingService.settleBillingSession(
                    CONVERSATION_ID, WORKSPACE_ID, USER_ID);

            assertNotNull(response);
            verify(billingSessionMapper, never()).updateStatusToSettling(any(), any());
        }

        @Test
        @DisplayName("超额消费封顶到冻结金额")
        void settleBillingSession_capsAtFrozenAmount() {
            AgentBillingSession session = createActiveSession(150, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);
            when(billingSessionMapper.updateStatusToSettling(eq(SESSION_ID), any())).thenReturn(1);

            AgentBillingSession latestSession = createActiveSession(150, 100);
            latestSession.setStatus("SETTLING");
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestSession);

            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            AgentBillingSession settledSession = createActiveSession(150, 100);
            settledSession.setStatus("SETTLED");
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session, settledSession);

            billingService.settleBillingSession(CONVERSATION_ID, WORKSPACE_ID, USER_ID);

            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(100L), any());
        }

        @Test
        @DisplayName("结算使用最新 DB 数据（冻结金额可能已追加）")
        void settleBillingSession_usesLatestDbData() {
            AgentBillingSession session = createActiveSession(80, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);
            when(billingSessionMapper.updateStatusToSettling(eq(SESSION_ID), any())).thenReturn(1);

            // 最新 DB 数据中冻结金额已被追加到 200
            AgentBillingSession latestSession = createActiveSession(80, 200);
            latestSession.setStatus("SETTLING");
            when(billingSessionMapper.selectById(SESSION_ID)).thenReturn(latestSession);

            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            AgentBillingSession settledSession = createActiveSession(80, 200);
            settledSession.setStatus("SETTLED");
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session, settledSession);

            billingService.settleBillingSession(CONVERSATION_ID, WORKSPACE_ID, USER_ID);

            // 应使用最新的 totalCost(80) 而不是被旧的 frozenAmount(100) 限制
            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(80L), any());
        }
    }

    @Nested
    @DisplayName("cancelBillingSession 取消测试")
    class CancelBillingSessionTests {

        @Test
        @DisplayName("正常取消并解冻")
        void cancelBillingSession_success() {
            AgentBillingSession session = createActiveSession(0, 100);
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);
            when(walletFeignClient.unfreeze(eq(WORKSPACE_ID), any()))
                    .thenReturn(Result.success());

            billingService.cancelBillingSession(CONVERSATION_ID, WORKSPACE_ID, USER_ID);

            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(0L), any());
        }

        @Test
        @DisplayName("非 ACTIVE 状态不执行取消")
        void cancelBillingSession_nonActiveIgnored() {
            AgentBillingSession session = createActiveSession(0, 100);
            session.setStatus("SETTLED");
            when(billingSessionMapper.selectByConversationId(CONVERSATION_ID)).thenReturn(session);

            billingService.cancelBillingSession(CONVERSATION_ID, WORKSPACE_ID, USER_ID);

            verify(walletFeignClient, never()).unfreeze(any(), any());
        }
    }
}
