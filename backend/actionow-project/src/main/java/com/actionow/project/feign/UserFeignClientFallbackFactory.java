package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("调用用户服务失败: {}", cause.getMessage());
        return new UserFeignClient() {
            @Override
            public Result<UserBasicInfo> getUserBasicInfo(String userId) {
                log.warn("获取用户信息降级: userId={}", userId);
                return Result.success(null);
            }

            @Override
            public Result<Map<String, UserBasicInfo>> batchGetUserBasicInfo(List<String> userIds) {
                log.warn("批量获取用户信息降级: userIds={}", userIds);
                return Result.success(Collections.emptyMap());
            }
        };
    }
}
