package com.actionow.billing.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 钱包内部接口客户端
 */
@FeignClient(name = "actionow-wallet", path = "/internal/wallet")
public interface WalletFeignClient {

    /**
     * 通过支付订单执行充值入账
     */
    @PostMapping("/{workspaceId}/topup")
    Result<Object> topup(@PathVariable("workspaceId") String workspaceId,
                         @RequestBody WalletTopupRequest request,
                         @RequestParam("operatorId") String operatorId);
}
