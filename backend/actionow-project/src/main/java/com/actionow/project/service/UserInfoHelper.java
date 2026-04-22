package com.actionow.project.service;

import com.actionow.project.feign.UserBasicInfo;

import java.util.Collection;
import java.util.Map;

/**
 * 用户信息帮助服务
 *
 * @author Actionow
 */
public interface UserInfoHelper {

    /**
     * 获取单个用户信息
     *
     * @param userId 用户ID
     * @return 用户基本信息，如果用户不存在返回null
     */
    UserBasicInfo getUserInfo(String userId);

    /**
     * 批量获取用户信息
     *
     * @param userIds 用户ID集合
     * @return 用户ID到用户基本信息的映射
     */
    Map<String, UserBasicInfo> batchGetUserInfo(Collection<String> userIds);
}
