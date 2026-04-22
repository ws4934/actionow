package com.actionow.agent.billing.compensation;

import com.actionow.agent.billing.entity.AgentBillingSession;
import com.actionow.agent.billing.mapper.AgentBillingSessionMapper;
import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.feign.WalletFeignClient;
import com.actionow.agent.feign.dto.ConfirmConsumeRequest;
import com.actionow.agent.feign.dto.UnfreezeRequest;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BillingCompensationHandler 单元测试
 *
 * @author Actionow
 */
@ExtendWith(MockitoExtension.class)
class BillingCompensationHandlerTest {

    @Mock
    private AgentBillingSessionMapper billingSessionMapper;

    @Mock
    private WalletFeignClient walletFeignClient;

    @Mock
    private AgentRuntimeConfigService agentRuntimeConfig;

    @InjectMocks
    private BillingCompensationHandler handler;

    private static final String SESSION_ID = "session-123";
    private static final String WORKSPACE_ID = "ws-123";
    private static final String USER_ID = "user-123";
    private static final String CONVERSATION_ID = "conv-123";

    private AgentBillingSession createSession(long totalCost, long frozenAmount, String settleError,
                                               LocalDateTime createdAt) {
        AgentBillingSession session = new AgentBillingSession();
        session.setId(SESSION_ID);
        session.setWorkspaceId(WORKSPACE_ID);
        session.setUserId(USER_ID);
        session.setConversationId(CONVERSATION_ID);
        session.setTotalCost(totalCost);
        session.setFrozenAmount(frozenAmount);
        session.setSettleError(settleError);
        session.setStatus("FAILED");
        session.setCreatedAt(createdAt);
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    @Nested
    @DisplayName("handleFailedSessions 批量处理测试")
    class HandleFailedSessionsTests {

        @Test
        @DisplayName("无失败会话时返回 0")
        void handleFailedSessions_noSessions() {
            when(billingSessionMapper.selectFailedSessions(anyInt())).thenReturn(Collections.emptyList());

            int result = handler.handleFailedSessions(50);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("成功处理失败会话")
        void handleFailedSessions_processesSuccessfully() {
            AgentBillingSession session = createSession(100, 200, null, LocalDateTime.now().minusHours(1));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(agentRuntimeConfig.getBillingMaxRetryCount()).thenReturn(3);
            when(agentRuntimeConfig.getBillingCompensationTimeoutHours()).thenReturn(24);
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            int result = handler.handleFailedSessions(50);
            assertEquals(1, result);

            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(100L), any());
        }
    }

    @Nested
    @DisplayName("retrySettle 重试结算测试")
    class RetrySettleTests {

        @BeforeEach
        void setUp() {
            when(agentRuntimeConfig.getBillingMaxRetryCount()).thenReturn(3);
            when(agentRuntimeConfig.getBillingCompensationTimeoutHours()).thenReturn(24);
        }

        @Test
        @DisplayName("首次重试成功")
        void retrySettle_firstRetrySuccess() {
            AgentBillingSession session = createSession(80, 100, null, LocalDateTime.now().minusMinutes(30));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(80L), any());
        }

        @Test
        @DisplayName("超额消费封顶到冻结金额")
        void retrySettle_capsAtFrozenAmount() {
            AgentBillingSession session = createSession(150, 100, null, LocalDateTime.now().minusMinutes(30));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            ArgumentCaptor<ConfirmConsumeRequest> captor = ArgumentCaptor.forClass(ConfirmConsumeRequest.class);
            verify(walletFeignClient).confirmConsume(eq(WORKSPACE_ID), captor.capture());
            assertEquals(100L, captor.getValue().getActualAmount());
        }

        @Test
        @DisplayName("达到最大重试次数后停止重试")
        void retrySettle_stopsAtMaxRetry() {
            AgentBillingSession session = createSession(80, 100, "[retry:3] 补偿结算失败: timeout",
                    LocalDateTime.now().minusMinutes(30));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));

            handler.handleFailedSessions(50);

            verify(walletFeignClient, never()).confirmConsume(any(), any());
            verify(billingSessionMapper).updateSettleError(eq(SESSION_ID),
                    contains("已达最大重试次数"), any());
        }

        @Test
        @DisplayName("重试失败时记录错误和重试次数")
        void retrySettle_recordsRetryCountOnFailure() {
            AgentBillingSession session = createSession(80, 100, "[retry:1] 补偿结算失败: 网络超时",
                    LocalDateTime.now().minusMinutes(30));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.fail("网络超时"));

            handler.handleFailedSessions(50);

            verify(billingSessionMapper).updateSettleError(eq(SESSION_ID),
                    contains("[retry:2]"), any());
        }
    }

    @Nested
    @DisplayName("releaseFreeze 超时释放测试")
    class ReleaseFreezeTests {

        @Test
        @DisplayName("超过 24 小时 - 有消费则先确认消费")
        void releaseFreeze_withConsumption_confirmsConsume() {
            when(agentRuntimeConfig.getBillingCompensationTimeoutHours()).thenReturn(24);
            AgentBillingSession session = createSession(80, 100, null, LocalDateTime.now().minusHours(25));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(80L), any());
            verify(walletFeignClient, never()).unfreeze(any(), any());
        }

        @Test
        @DisplayName("超过 24 小时 - 无消费则直接解冻")
        void releaseFreeze_noConsumption_unfreezesDirectly() {
            when(agentRuntimeConfig.getBillingCompensationTimeoutHours()).thenReturn(24);
            AgentBillingSession session = createSession(0, 100, null, LocalDateTime.now().minusHours(25));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(walletFeignClient.unfreeze(eq(WORKSPACE_ID), any(UnfreezeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            verify(billingSessionMapper).updateStatusToSettled(eq(SESSION_ID), eq(0L), any());
            verify(walletFeignClient, never()).confirmConsume(any(), any());
        }

        @Test
        @DisplayName("超过 24 小时 - 消费超额按冻结额封顶")
        void releaseFreeze_overageConsumption_capsAtFrozen() {
            when(agentRuntimeConfig.getBillingCompensationTimeoutHours()).thenReturn(24);
            AgentBillingSession session = createSession(200, 100, null, LocalDateTime.now().minusHours(25));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            ArgumentCaptor<ConfirmConsumeRequest> captor = ArgumentCaptor.forClass(ConfirmConsumeRequest.class);
            verify(walletFeignClient).confirmConsume(eq(WORKSPACE_ID), captor.capture());
            assertEquals(100L, captor.getValue().getActualAmount());
        }

        @Test
        @DisplayName("可配置的超时时间")
        void releaseFreeze_configurableTimeout() {
            // 设置超时为 12 小时
            when(agentRuntimeConfig.getBillingCompensationTimeoutHours()).thenReturn(12);

            // 创建时间为 13 小时前 - 应该触发释放
            AgentBillingSession session = createSession(0, 100, null, LocalDateTime.now().minusHours(13));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(walletFeignClient.unfreeze(eq(WORKSPACE_ID), any(UnfreezeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            verify(walletFeignClient).unfreeze(eq(WORKSPACE_ID), any(UnfreezeRequest.class));
        }
    }

    @Nested
    @DisplayName("parseRetryCount 重试次数解析测试")
    class ParseRetryCountTests {

        @BeforeEach
        void setUp() {
            when(agentRuntimeConfig.getBillingCompensationTimeoutHours()).thenReturn(24);
        }

        @Test
        @DisplayName("null settleError 返回 0 次重试")
        void parseRetryCount_null() {
            AgentBillingSession session = createSession(80, 100, null, LocalDateTime.now().minusMinutes(10));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(agentRuntimeConfig.getBillingMaxRetryCount()).thenReturn(3);
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            // 如果 retryCount=0 < 3, 应执行重试
            verify(walletFeignClient).confirmConsume(eq(WORKSPACE_ID), any());
        }

        @Test
        @DisplayName("正确解析 [retry:2] 格式")
        void parseRetryCount_validFormat() {
            AgentBillingSession session = createSession(80, 100, "[retry:2] 补偿结算失败: xxx",
                    LocalDateTime.now().minusMinutes(10));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(agentRuntimeConfig.getBillingMaxRetryCount()).thenReturn(3);
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            // retryCount=2 < 3, 应执行重试
            verify(walletFeignClient).confirmConsume(eq(WORKSPACE_ID), any());
        }

        @Test
        @DisplayName("无前缀格式返回 0 次重试")
        void parseRetryCount_noPrefix() {
            AgentBillingSession session = createSession(80, 100, "结算失败: 网络异常",
                    LocalDateTime.now().minusMinutes(10));
            when(billingSessionMapper.selectFailedSessions(50)).thenReturn(List.of(session));
            when(agentRuntimeConfig.getBillingMaxRetryCount()).thenReturn(3);
            when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                    .thenReturn(Result.success());

            handler.handleFailedSessions(50);

            verify(walletFeignClient).confirmConsume(eq(WORKSPACE_ID), any());
        }
    }
}
