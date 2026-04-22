package com.actionow.task.feign;

import com.actionow.common.core.result.Result;
import com.actionow.task.dto.ConfirmConsumeRequest;
import com.actionow.task.dto.FreezeRequest;
import com.actionow.task.dto.FreezeResponse;
import com.actionow.task.dto.UnfreezeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * WalletFeignClientFallbackFactory 降级行为回归测试
 *
 * 重点验证（P0 修复回归保护）：
 *  - 金额相关降级全部 fail-closed，禁止任何形式的 Result.success 假阳性
 *  - 配额类校验返回业务失败而不是静默放行，避免超额消费
 *  - 解冻/退还退款失败时不可返回成功，否则补偿链会误 markCompleted
 *
 * 历史背景：
 *  早期版本 checkQuota / useQuota / refundQuota 在 fallback 中返回 Result.success(true)，
 *  属于典型 fail-open 反模式 —— 钱包宕机时上层以为一切正常，直接触发用户被免费使用、
 *  资金丢失或补偿丢失等严重事故。此测试绑死 fail-closed 行为，防止回归。
 *
 * @author Actionow
 */
class WalletFeignClientFallbackFactoryTest {

    private static final String ERROR_CODE = "50003";

    private WalletFeignClient fallback;

    @BeforeEach
    void setUp() {
        WalletFeignClientFallbackFactory factory = new WalletFeignClientFallbackFactory();
        // 模拟一个钱包服务不可用的异常触发 fallback
        fallback = factory.create(new RuntimeException("wallet service unavailable"));
    }

    @Test
    @DisplayName("freeze 降级：返回失败，不得 success")
    void freezeShouldFailClosed() {
        FreezeRequest request = FreezeRequest.builder()
                .workspaceId("ws-1")
                .operatorId("u-1")
                .amount(100L)
                .businessId("biz-1")
                .build();

        Result<FreezeResponse> result = fallback.freeze("ws-1", request);

        assertFalse(result.isSuccess(), "freeze fallback 必须 fail-closed");
        assertEquals(ERROR_CODE, result.getCode());
    }

    @Test
    @DisplayName("confirmConsume 降级：返回失败，不得 success")
    void confirmConsumeShouldFailClosed() {
        ConfirmConsumeRequest request = ConfirmConsumeRequest.builder()
                .workspaceId("ws-1")
                .operatorId("u-1")
                .businessId("biz-1")
                .actualAmount(50L)
                .build();

        Result<Void> result = fallback.confirmConsume("ws-1", request);

        assertFalse(result.isSuccess(), "confirmConsume fallback 必须 fail-closed");
        assertEquals(ERROR_CODE, result.getCode());
    }

    @Test
    @DisplayName("unfreeze 降级：返回失败，不得 success（否则补偿链会误 markCompleted）")
    void unfreezeShouldFailClosed() {
        UnfreezeRequest request = UnfreezeRequest.builder()
                .workspaceId("ws-1")
                .operatorId("u-1")
                .businessId("biz-1")
                .build();

        Result<Void> result = fallback.unfreeze("ws-1", request);

        assertFalse(result.isSuccess(), "unfreeze fallback 必须 fail-closed");
        assertEquals(ERROR_CODE, result.getCode());
    }

    @Test
    @DisplayName("checkQuota 降级：不得 fail-open 放行（历史漏洞）")
    void checkQuotaShouldFailClosed() {
        Result<Boolean> result = fallback.checkQuota("ws-1", "u-1", 100L);

        assertFalse(result.isSuccess(), "checkQuota fallback 必须 fail-closed，不得放行");
        assertEquals(ERROR_CODE, result.getCode());
    }

    @Test
    @DisplayName("useQuota 降级：不得返回 success(true)，否则上层误以为配额已占用")
    void useQuotaShouldFailClosed() {
        Result<Boolean> result = fallback.useQuota("ws-1", "u-1", 100L);

        assertFalse(result.isSuccess(), "useQuota fallback 必须 fail-closed");
        assertEquals(ERROR_CODE, result.getCode());
    }

    @Test
    @DisplayName("refundQuota 降级：不得返回 success(true)，否则补偿链会误 markCompleted 丢失退款")
    void refundQuotaShouldFailClosed() {
        Result<Boolean> result = fallback.refundQuota("ws-1", "u-1", 100L);

        assertFalse(result.isSuccess(), "refundQuota fallback 必须 fail-closed");
        assertEquals(ERROR_CODE, result.getCode());
    }
}
