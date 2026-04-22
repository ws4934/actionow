package com.actionow.project.service.impl;

import com.actionow.common.core.result.Result;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.project.feign.UserFeignClient;
import com.actionow.project.service.UserInfoHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户信息帮助服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInfoHelperImpl implements UserInfoHelper {

    private final UserFeignClient userFeignClient;

    @Override
    public UserBasicInfo getUserInfo(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }

        try {
            Result<UserBasicInfo> result = userFeignClient.getUserBasicInfo(userId);
            if (result != null && result.isSuccess()) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: userId={}, error={}", userId, e.getMessage());
        }

        return null;
    }

    @Override
    public Map<String, UserBasicInfo> batchGetUserInfo(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 去重并过滤空值
        List<String> distinctUserIds = userIds.stream()
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (distinctUserIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Result<Map<String, UserBasicInfo>> result = userFeignClient.batchGetUserBasicInfo(distinctUserIds);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: userIds={}, error={}", distinctUserIds, e.getMessage());
        }

        return Collections.emptyMap();
    }
}
