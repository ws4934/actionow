package com.actionow.workspace.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-user", path = "/internal/users")
public interface UserFeignClient {

    /**
     * 根据用户ID获取基本信息
     */
    @GetMapping("/{userId}/basic")
    Result<UserBasicInfo> getUserBasicInfo(@PathVariable("userId") String userId);

    /**
     * 批量获取用户基本信息
     */
    @PostMapping("/batch/basic")
    Result<Map<String, UserBasicInfo>> batchGetUserBasicInfo(@RequestBody List<String> userIds);
}
