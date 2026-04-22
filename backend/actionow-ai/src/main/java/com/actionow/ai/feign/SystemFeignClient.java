package com.actionow.ai.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * System 服务 Feign 客户端
 * 用于获取系统配置（如 API Keys）
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-system", path = "/internal/system",
        fallbackFactory = SystemFeignClientFallbackFactory.class)
public interface SystemFeignClient {

    /**
     * 获取全局配置值
     *
     * @param configKey 配置键
     * @return 配置值
     */
    @GetMapping("/config/value")
    Result<String> getConfigValue(@RequestParam("configKey") String configKey);
}
