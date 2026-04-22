package com.actionow.task.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端
 * 用于批量查询用户名称
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-user", contextId = "taskUserFeignClient",
        path = "/internal/users", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 批量获取用户基本信息
     *
     * @param userIds 用户ID列表
     * @return 用户ID到基本信息的映射
     */
    @PostMapping("/batch/basic")
    Result<Map<String, UserBasicInfo>> batchGetUserBasicInfo(@RequestBody List<String> userIds);
}
