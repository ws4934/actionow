package com.actionow.agent.feign;

import org.springframework.stereotype.Component;

/**
 * AI 服务 Feign 客户端降级工厂
 * <p>
 * List 返回类型自动降级为空列表，其他返回 Result.fail("50001", ...)。
 *
 * @author Actionow
 */
@Component
public class AiFeignClientFallbackFactory extends AbstractFeignFallbackFactory<AiFeignClient> {

    @Override
    protected String serviceName() {
        return "AI 服务";
    }

    @Override
    protected String errorCode() {
        return "50001";
    }
}
