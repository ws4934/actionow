package com.actionow.agent.billing.service;

import com.actionow.agent.billing.dto.TokenUsageRecord;
import com.actionow.agent.billing.exception.InsufficientCreditsException;
import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.feign.WalletFeignClient;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BillingIntegrationService 单元测试
 *
 * 重点验证 InsufficientCreditsException 的传播语义：
 *  - 积分不足时必须向上传播（触发 Agent 停止生成/调用工具）
 *  - 其他 Exception 仍按原有策略吞掉（避免非关键计费异常中断会话）
 *
 * @author Actionow
 */
@ExtendWith(MockitoExtension.class)
class BillingIntegrationServiceTest {

    @Mock
    private AgentBillingService billingService;

    @Mock
    private AgentBillingCalculator billingCalculator;

    @Mock
    private WalletFeignClient walletFeignClient;

    @InjectMocks
    private BillingIntegrationService integrationService;

    private static final String CONVERSATION_ID = "conv-123";
    private static final String MESSAGE_ID = "msg-1";

    private AgentStreamEvent.TokenUsage buildUsage(long input, long output, long thought, long cached) {
        return AgentStreamEvent.TokenUsage.builder()
                .promptTokens(input)
                .completionTokens(output)
                .thoughtTokens(thought)
                .cachedTokens(cached)
                .totalTokens(input + output + thought)
                .build();
    }

    @Nested
    @DisplayName("recordTokenUsage 异常传播语义")
    class RecordTokenUsageExceptionPropagation {

        @Test
        @DisplayName("InsufficientCreditsException 必须向上传播")
        void insufficientCredits_propagated() {
            AgentStreamEvent.TokenUsage usage = buildUsage(100, 50, 20, 10);
            when(billingService.recordTokenUsage(eq(CONVERSATION_ID), eq(MESSAGE_ID),
                    eq(100), eq(50), eq(20), eq(10)))
                    .thenThrow(new InsufficientCreditsException("积分余额不足"));

            // 关键断言：积分不足必须抛出，以触发 Agent 停止
            assertThrows(InsufficientCreditsException.class, () ->
                    integrationService.recordTokenUsage(CONVERSATION_ID, MESSAGE_ID, usage));
        }

        @Test
        @DisplayName("通用异常被吞掉（保护会话连续性）")
        void genericException_swallowed() {
            AgentStreamEvent.TokenUsage usage = buildUsage(100, 50, 0, 0);
            when(billingService.recordTokenUsage(anyString(), anyString(),
                    anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB connection timeout"));

            // 通用异常不应抛出，会话继续
            assertDoesNotThrow(() ->
                    integrationService.recordTokenUsage(CONVERSATION_ID, MESSAGE_ID, usage));
        }

        @Test
        @DisplayName("SETTLED 会话异常被吞掉")
        void settledSessionException_swallowed() {
            AgentStreamEvent.TokenUsage usage = buildUsage(100, 50, 0, 0);
            when(billingService.recordTokenUsage(anyString(), anyString(),
                    anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenThrow(new BusinessException(ResultCode.FAIL,
                            "计费会话状态不正确: status=SETTLED"));

            assertDoesNotThrow(() ->
                    integrationService.recordTokenUsage(CONVERSATION_ID, MESSAGE_ID, usage));
        }

        @Test
        @DisplayName("null TokenUsage 直接返回，不调用底层服务")
        void nullUsage_noop() {
            integrationService.recordTokenUsage(CONVERSATION_ID, MESSAGE_ID, null);
            verify(billingService, never()).recordTokenUsage(
                    any(), any(), anyInt(), anyInt(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("全 0 Token 不调用底层记录")
        void zeroTokens_skipped() {
            AgentStreamEvent.TokenUsage usage = buildUsage(0, 0, 0, 10);
            integrationService.recordTokenUsage(CONVERSATION_ID, MESSAGE_ID, usage);
            verify(billingService, never()).recordTokenUsage(
                    any(), any(), anyInt(), anyInt(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("正常记录调用底层服务")
        void normalRecording_delegated() {
            AgentStreamEvent.TokenUsage usage = buildUsage(100, 50, 20, 10);
            TokenUsageRecord record = TokenUsageRecord.builder()
                    .billingSessionId("session-1")
                    .messageId(MESSAGE_ID)
                    .cost(5L)
                    .build();
            when(billingService.recordTokenUsage(eq(CONVERSATION_ID), eq(MESSAGE_ID),
                    eq(100), eq(50), eq(20), eq(10)))
                    .thenReturn(record);

            integrationService.recordTokenUsage(CONVERSATION_ID, MESSAGE_ID, usage);

            verify(billingService).recordTokenUsage(eq(CONVERSATION_ID), eq(MESSAGE_ID),
                    eq(100), eq(50), eq(20), eq(10));
        }
    }

    @Nested
    @DisplayName("recordAiToolUsage 异常传播语义")
    class RecordAiToolUsageExceptionPropagation {

        @Test
        @DisplayName("InsufficientCreditsException 必须向上传播")
        void insufficientCredits_propagated() {
            doThrow(new InsufficientCreditsException("积分余额不足"))
                    .when(billingService).recordAiToolUsage(eq(CONVERSATION_ID), eq("image-gen"), eq(50L));

            // 关键断言：工具调用层同样必须传播积分不足信号
            assertThrows(InsufficientCreditsException.class, () ->
                    integrationService.recordAiToolUsage(CONVERSATION_ID, "image-gen", 50L));
        }

        @Test
        @DisplayName("通用异常被吞掉（保护会话连续性）")
        void genericException_swallowed() {
            doThrow(new RuntimeException("DB connection timeout"))
                    .when(billingService).recordAiToolUsage(anyString(), anyString(), anyLong());

            assertDoesNotThrow(() ->
                    integrationService.recordAiToolUsage(CONVERSATION_ID, "image-gen", 50L));
        }

        @Test
        @DisplayName("正常记录调用底层服务")
        void normalRecording_delegated() {
            integrationService.recordAiToolUsage(CONVERSATION_ID, "image-gen", 50L);
            verify(billingService).recordAiToolUsage(eq(CONVERSATION_ID), eq("image-gen"), eq(50L));
        }
    }
}
