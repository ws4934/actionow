package com.actionow.collab.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 资产服务 Feign 客户端
 * 备用接口 — 主路径通过前端传入冗余元数据，不依赖运行时 Feign 调用
 *
 * @author Actionow
 */
@FeignClient(
        name = "actionow-project",
        path = "/internal/assets",
        fallbackFactory = AssetFeignClientFallbackFactory.class
)
public interface AssetFeignClient {

    /**
     * 批量查询资产元数据
     *
     * @param ids 资产ID列表
     * @return 资产元数据列表
     */
    @GetMapping("/batch")
    Result<List<Map<String, Object>>> batchGetAssets(@RequestParam("ids") List<String> ids);
}
