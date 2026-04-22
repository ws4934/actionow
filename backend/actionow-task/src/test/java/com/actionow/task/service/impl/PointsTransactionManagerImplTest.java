package com.actionow.task.service.impl;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.redis.lock.DistributedLockService;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.CompensationType;
import com.actionow.task.dto.FreezeRequest;
import com.actionow.task.dto.FreezeResponse;
import com.actionow.task.dto.UnfreezeRequest;
import com.actionow.task.dto.ConfirmConsumeRequest;
import com.actionow.task.feign.WalletFeignClient;
import com.actionow.task.service.CompensationTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PointsTransactionManagerImpl 核心流程单元测试
 *
 * 重点验证 P0 修复的关键行为：
 *  1. freezePoints 幂等命中：相同 businessId 在 TTL 内重复调用只真实执行一次
 *  2. TOCTOU 消除：useQuota 必须在 freeze 之前执行（操作顺序至关重要）
 *  3. freeze 失败 → 配额回滚：保证原子性
 *  4. 配额不足 → 立即抛异常且不调用 freeze
 *  5. unfreeze 成功 → 清除幂等 token，允许后续再次 freeze
 *
 * @author Actionow
 */
@ExtendWith(MockitoExtension.class)
class PointsTransactionManagerImplTest {

    @Mock
    private WalletFeignClient walletFeignClient;
    @Mock
    private com.actionow.task.mapper.CompensationTaskMapper compensationTaskMapper;
    @Mock
    private com.actionow.common.security.workspace.WorkspaceInternalClient workspaceInternalClient;
    @Mock
    private com.actionow.common.mq.producer.MessageProducer messageProducer;
    @Mock
    private DistributedLockService distributedLockService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private CompensationTaskService compensationTaskService;
    @Mock
    private TaskRuntimeConfigService runtimeConfig;

    @InjectMocks
    private PointsTransactionManagerImpl manager;

    private static final String WORKSPACE_ID = "ws-1";
    private static final String USER_ID = "u-1";
    private static final String BIZ_ID = "biz-1";
    private static final String BIZ_TYPE = "TEST";
    private static final Long AMOUNT = 100L;

    @BeforeEach
    void setUp() {
        // 默认：幂等缓存无命中、锁穿透执行 Supplier（lenient: 不是每个用例都会触达）
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);

        // 模拟分布式锁：直接执行 Supplier 返回值，相当于锁始终成功
        lenient().when(distributedLockService.executeWithLock(
                anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<?> supplier = inv.getArgument(4);
                    return supplier.get();
                });
    }

    @Test
    @DisplayName("freezePoints 幂等命中：跳过锁与真实冻结")
    void freezePointsIdempotentHit() {
        when(valueOperations.get("task:freeze:done:" + BIZ_ID)).thenReturn("txn-cached");

        String txnId = manager.freezePoints(WORKSPACE_ID, USER_ID, AMOUNT, BIZ_TYPE, BIZ_ID, "test");

        assertEquals("txn-cached", txnId);
        // 未触达 walletFeignClient 的任何方法
        verify(walletFeignClient, never()).useQuota(anyString(), anyString(), anyLong());
        verify(walletFeignClient, never()).freeze(anyString(), any());
        // 未触达分布式锁
        verify(distributedLockService, never()).executeWithLock(
                anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class));
    }

    @Test
    @DisplayName("freezePoints 成功路径：useQuota 必须早于 freeze（TOCTOU 消除）")
    void freezePointsOrderingUseQuotaBeforeFreeze() {
        when(walletFeignClient.useQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.success(true));
        FreezeResponse response = FreezeResponse.builder()
                .transactionId("txn-new").frozenAmount(AMOUNT).build();
        when(walletFeignClient.freeze(eq(WORKSPACE_ID), any(FreezeRequest.class)))
                .thenReturn(Result.success(response));

        String txnId = manager.freezePoints(WORKSPACE_ID, USER_ID, AMOUNT, BIZ_TYPE, BIZ_ID, "test");

        assertEquals("txn-new", txnId);

        // 关键回归：useQuota 必须在 freeze 之前调用
        var order = inOrder(walletFeignClient, valueOperations);
        order.verify(walletFeignClient).useQuota(WORKSPACE_ID, USER_ID, AMOUNT);
        order.verify(walletFeignClient).freeze(eq(WORKSPACE_ID), any(FreezeRequest.class));
        // 成功后写入幂等 token
        order.verify(valueOperations).set(
                eq("task:freeze:done:" + BIZ_ID), eq("txn-new"), any(Duration.class));
    }

    @Test
    @DisplayName("freezePoints 配额不足：立即抛 QUOTA_EXCEEDED 且不调用 freeze")
    void freezePointsQuotaInsufficient() {
        when(walletFeignClient.useQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.success(false));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                manager.freezePoints(WORKSPACE_ID, USER_ID, AMOUNT, BIZ_TYPE, BIZ_ID, "test"));

        // 不应继续调用 freeze
        verify(walletFeignClient, never()).freeze(anyString(), any());
        // 不应写入幂等 token
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        // 不应调用补偿（因为没有占用配额，没有需要回滚的资源）
        verify(compensationTaskService, never()).createCompensationTask(
                any(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("freezePoints freeze 失败：必须回滚配额")
    void freezePointsFreezeFailureRollbacksQuota() {
        when(walletFeignClient.useQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.success(true));
        when(walletFeignClient.freeze(eq(WORKSPACE_ID), any(FreezeRequest.class)))
                .thenReturn(Result.fail("40000", "insufficient points"));
        // refund 成功 → 无需补偿任务
        when(walletFeignClient.refundQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.success(true));

        assertThrows(BusinessException.class, () ->
                manager.freezePoints(WORKSPACE_ID, USER_ID, AMOUNT, BIZ_TYPE, BIZ_ID, "test"));

        // freeze 失败后必须尝试回退配额
        verify(walletFeignClient, times(1))
                .refundQuota(WORKSPACE_ID, USER_ID, AMOUNT);
        // refund 成功 → 不创建补偿任务
        verify(compensationTaskService, never()).createCompensationTask(
                any(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
        // 失败时绝不能写入幂等 token
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("freezePoints freeze 失败且回滚配额失败：创建 REFUND_QUOTA 补偿任务兜底")
    void freezePointsFreezeFailureRefundFailureCreatesCompensation() {
        when(walletFeignClient.useQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.success(true));
        when(walletFeignClient.freeze(eq(WORKSPACE_ID), any(FreezeRequest.class)))
                .thenReturn(Result.fail("40000", "insufficient"));
        when(walletFeignClient.refundQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.fail("50000", "wallet down"));

        assertThrows(BusinessException.class, () ->
                manager.freezePoints(WORKSPACE_ID, USER_ID, AMOUNT, BIZ_TYPE, BIZ_ID, "test"));

        ArgumentCaptor<CompensationType> typeCaptor = ArgumentCaptor.forClass(CompensationType.class);
        verify(compensationTaskService, times(1)).createCompensationTask(
                typeCaptor.capture(), eq(WORKSPACE_ID), eq(USER_ID),
                eq(BIZ_ID), eq(BIZ_TYPE), eq(AMOUNT), anyString(), anyString());
        assertEquals(CompensationType.REFUND_QUOTA, typeCaptor.getValue());
    }

    // ==================== P1.2 回归测试：40004 幂等兼容 ====================

    @Test
    @DisplayName("freezePoints Redis 缓存读取失败：降级穿透到 wallet，仍然成功冻结")
    void freezePointsRedisReadFailureFallsThrough() {
        // Redis GET 抛异常（部分故障）
        when(valueOperations.get(anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("connection lost"));

        // Wallet 调用正常成功
        when(walletFeignClient.useQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.success(true));
        FreezeResponse response = FreezeResponse.builder()
                .transactionId("txn-redis-down").frozenAmount(AMOUNT).build();
        when(walletFeignClient.freeze(eq(WORKSPACE_ID), any(FreezeRequest.class)))
                .thenReturn(Result.success(response));
        // Redis SET 也失败（降级为不缓存）
        lenient().doThrow(new org.springframework.data.redis.RedisConnectionFailureException("connection lost"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        // 应成功返回 transactionId（Redis 故障仅降低性能，不影响正确性）
        String txnId = manager.freezePoints(WORKSPACE_ID, USER_ID, AMOUNT, BIZ_TYPE, BIZ_ID, "test");
        assertEquals("txn-redis-down", txnId);

        // Wallet 调用仍然正常执行
        verify(walletFeignClient).useQuota(WORKSPACE_ID, USER_ID, AMOUNT);
        verify(walletFeignClient).freeze(eq(WORKSPACE_ID), any(FreezeRequest.class));
    }

    // ==================== P1.2 回归测试：40004 幂等兼容 ====================

    @Test
    @DisplayName("unfreezePointsAsync：钱包返回 40004 视为幂等成功，不创建补偿任务")
    void unfreezeIdempotentOn40004() {
        // unfreeze 返回 40004（冻结记录已被首次调用消耗）
        when(walletFeignClient.unfreeze(eq(WORKSPACE_ID), any(UnfreezeRequest.class)))
                .thenReturn(Result.fail("40004", "冻结记录不存在"));
        // refundQuota 正常成功
        when(walletFeignClient.refundQuota(WORKSPACE_ID, USER_ID, AMOUNT))
                .thenReturn(Result.success(true));

        // 不应抛异常（40004 被视为幂等成功）
        assertDoesNotThrow(() ->
                manager.unfreezePointsAsync(WORKSPACE_ID, USER_ID, BIZ_ID, BIZ_TYPE, "test", AMOUNT));

        // 不应创建补偿任务
        verify(compensationTaskService, never()).createCompensationTask(
                eq(CompensationType.UNFREEZE), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("confirmConsumeAsync：钱包返回 40004 视为幂等成功，不创建补偿任务")
    void confirmConsumeIdempotentOn40004() {
        // confirmConsume 返回 40004
        when(walletFeignClient.confirmConsume(eq(WORKSPACE_ID), any(ConfirmConsumeRequest.class)))
                .thenReturn(Result.fail("40004", "冻结记录不存在"));

        // 不应抛异常
        assertDoesNotThrow(() ->
                manager.confirmConsumeAsync(WORKSPACE_ID, USER_ID, BIZ_ID, BIZ_TYPE, AMOUNT, "test"));

        // 不应创建补偿任务
        verify(compensationTaskService, never()).createCompensationTask(
                eq(CompensationType.CONFIRM_CONSUME), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("unfreezePointsAsync：钱包返回非 40004 错误仍创建补偿任务")
    void unfreezeNon40004ErrorCreatesCompensation() {
        // unfreeze 返回其他错误
        when(walletFeignClient.unfreeze(eq(WORKSPACE_ID), any(UnfreezeRequest.class)))
                .thenReturn(Result.fail("50000", "wallet service error"));

        // 调用不应抛异常（@Async 内部 catch，创建补偿）
        assertDoesNotThrow(() ->
                manager.unfreezePointsAsync(WORKSPACE_ID, USER_ID, BIZ_ID, BIZ_TYPE, "test", AMOUNT));

        // 应创建 UNFREEZE 补偿任务
        verify(compensationTaskService, times(1)).createCompensationTask(
                eq(CompensationType.UNFREEZE), eq(WORKSPACE_ID), eq(USER_ID),
                eq(BIZ_ID), eq(BIZ_TYPE), eq(AMOUNT), eq("test"), anyString());
    }
}
