package com.actionow.agent.feign;

import org.springframework.stereotype.Component;

/**
 * 钱包服务 Feign 客户端降级工厂
 * <p>
 * Result&lt;Boolean&gt; 方法（checkQuota/useQuota/refundQuota）降级返回 true（放行），
 * 其他方法返回 Result.fail("50003", ...) — 由基类自动处理。
 *
 * @author Actionow
 */
@Component
public class WalletFeignClientFallbackFactory extends AbstractFeignFallbackFactory<WalletFeignClient> {

    @Override
    protected String serviceName() {
        return "钱包服务";
    }

    @Override
    protected String errorCode() {
        return "50003";
    }
}
