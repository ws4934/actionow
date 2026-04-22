package com.actionow.project.feign;

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
 * 供其他微服务调用用户服务内部接口
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-user", path = "/internal/users", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 根据用户ID获取基本信息
     *
     * @param userId 用户ID
     * @return 用户基本信息
     */
    @GetMapping("/{userId}/basic")
    Result<UserBasicInfo> getUserBasicInfo(@PathVariable("userId") String userId);

    /**
     * 批量获取用户基本信息
     *
     * @param userIds 用户ID列表
     * @return 用户ID到基本信息的映射
     */
    @PostMapping("/batch/basic")
    Result<Map<String, UserBasicInfo>> batchGetUserBasicInfo(@RequestBody List<String> userIds);
}
