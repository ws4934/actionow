package com.actionow.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.actionow.user.dto.request.BindTargetRequest;
import com.actionow.user.dto.request.UpdateProfileRequest;
import com.actionow.user.dto.response.UserResponse;
import com.actionow.user.entity.User;

import java.util.Optional;

/**
 * 用户服务接口
 *
 * @author Actionow
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查询
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据手机号查询
     */
    Optional<User> findByPhone(String phone);

    /**
     * 根据登录凭证查询（用户名/邮箱/手机号）
     */
    Optional<User> findByCredential(String credential);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 检查手机号是否存在
     */
    boolean existsByPhone(String phone);

    /**
     * 创建用户
     */
    User createUser(String username, String email, String password, String nickname);

    /**
     * 创建用户（支持手机号注册）
     */
    User createUser(String username, String email, String phone, String password, String nickname);

    /**
     * 创建用户（支持邀请人信息）
     */
    User createUser(String username, String email, String phone, String password, String nickname,
                    String invitedBy, String invitationCodeUsed);

    /**
     * 更新最后登录信息
     */
    void updateLastLogin(String userId, String ip);

    /**
     * 增加登录失败次数
     */
    void incrementLoginFailCount(String userId);

    /**
     * 重置登录失败次数
     */
    void resetLoginFailCount(String userId);

    /**
     * 锁定账号
     */
    void lockAccount(String userId, int minutes);

    /**
     * 检查账号是否锁定
     */
    boolean isAccountLocked(User user);

    /**
     * 更新密码
     */
    void updatePassword(String userId, String newPassword);

    /**
     * 更新用户资料
     */
    void updateProfile(String userId, UpdateProfileRequest request);

    /**
     * 绑定邮箱或手机
     */
    void bindTarget(String userId, BindTargetRequest request);

    /**
     * 转换为响应DTO
     */
    UserResponse toResponse(User user);
}
