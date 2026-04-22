package com.actionow.agent.feign;

import org.springframework.stereotype.Component;

/**
 * AssetFeignClient 降级处理工厂
 * <p>
 * List 返回类型自动降级为空列表，其他返回 Result.fail(...)。
 *
 * @author Actionow
 */
@Component
public class AssetFeignClientFallbackFactory extends AbstractFeignFallbackFactory<AssetFeignClient> {

    @Override
    protected String serviceName() {
        return "Asset 服务";
    }

    @Override
    protected String errorCode() {
        return "50002";
    }
}
