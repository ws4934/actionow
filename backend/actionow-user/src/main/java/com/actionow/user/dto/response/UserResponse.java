package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /**
     * 用户ID
     */
    private String id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号（脱敏）
     */
    private String phone;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 邮箱是否已验证
     */
    private Boolean emailVerified;

    /**
     * 手机是否已验证
     */
    private Boolean phoneVerified;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 绑定的OAuth提供商列表
     */
    private List<String> oauthProviders;

    /**
     * 用户在系统工作空间的角色（null 表示非系统成员）
     * 可选值: Creator, Admin, Member, Guest
     */
    private String systemRole;
}
