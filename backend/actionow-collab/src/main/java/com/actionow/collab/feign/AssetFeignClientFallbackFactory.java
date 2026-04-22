package com.actionow.collab.feign;

import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Asset Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AssetFeignClientFallbackFactory implements FallbackFactory<AssetFeignClient> {

    @Override
    public AssetFeignClient create(Throwable cause) {
        log.error("调用Asset服务失败: {}", cause.getMessage());

        return new AssetFeignClient() {
            @Override
            public Result<List<Map<String, Object>>> batchGetAssets(List<String> ids) {
                log.warn("批量查询资产降级: Asset服务不可用");
                return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), "Asset服务不可用");
            }
        };
    }
}
