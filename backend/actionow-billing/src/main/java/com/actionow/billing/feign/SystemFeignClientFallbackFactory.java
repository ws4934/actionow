package com.actionow.billing.feign;

import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * System Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class SystemFeignClientFallbackFactory implements FallbackFactory<SystemFeignClient> {

    @Override
    public SystemFeignClient create(Throwable cause) {
        log.error("System 服务调用失败: {}", cause.getMessage());
        return new SystemFeignClient() {
            @Override
            public Result<String> getConfigValue(String configKey) {
                log.warn("获取配置降级: configKey={}", configKey);
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(),
                        "System 服务不可用: " + cause.getMessage());
            }
        };
    }
}
