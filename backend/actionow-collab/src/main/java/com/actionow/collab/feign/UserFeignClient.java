package com.actionow.collab.feign;

import com.actionow.collab.dto.TokenValidateRequest;
import com.actionow.collab.dto.TokenValidateResponse;
import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端
 *
 * @author Actionow
 */
@FeignClient(
        name = "actionow-user",
        path = "/internal/users",
        fallbackFactory = UserFeignClientFallbackFactory.class
)
public interface UserFeignClient {

    /**
     * 验证Token并获取用户信息
     *
     * @param request Token验证请求
     * @return Token验证结果，包含用户信息
     */
    @PostMapping("/token/validate")
    Result<TokenValidateResponse> validateToken(@RequestBody TokenValidateRequest request);

    /**
     * 批量获取用户基本信息
     *
     * @param userIds 用户ID列表
     * @return 用户ID到基本信息的映射
     */
    @PostMapping("/batch/basic")
    Result<Map<String, UserBasicInfo>> batchGetUserBasicInfo(@RequestBody List<String> userIds);
}
