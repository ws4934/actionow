package com.actionow.billing.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工作空间内部接口客户端
 */
@FeignClient(name = "actionow-workspace", path = "/internal/workspace")
public interface WorkspaceFeignClient {

    /**
     * 内部同步工作空间订阅计划
     */
    @PostMapping("/{workspaceId}/plan")
    Result<Void> updatePlanInternal(@PathVariable("workspaceId") String workspaceId,
                                    @RequestParam("planType") String planType,
                                    @RequestParam("operatorId") String operatorId);
}
