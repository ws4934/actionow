package com.actionow.user.service;

import com.actionow.user.enums.VerifyCodeType;

/**
 * 验证码服务接口
 *
 * @author Actionow
 */
public interface VerifyCodeService {

    /**
     * 发送验证码
     *
     * @param target 目标（手机号或邮箱）
     * @param type   验证码类型
     * @return 过期时间（秒）
     */
    int sendVerifyCode(String target, VerifyCodeType type);

    /**
     * 验证验证码
     *
     * @param target     目标（手机号或邮箱）
     * @param type       验证码类型
     * @param verifyCode 验证码
     * @return 是否有效
     */
    boolean validateVerifyCode(String target, VerifyCodeType type, String verifyCode);

    /**
     * 验证并删除验证码
     *
     * @param target     目标（手机号或邮箱）
     * @param type       验证码类型
     * @param verifyCode 验证码
     * @return 是否有效
     */
    boolean validateAndDeleteVerifyCode(String target, VerifyCodeType type, String verifyCode);

    /**
     * 删除验证码
     *
     * @param target 目标
     * @param type   验证码类型
     */
    void deleteVerifyCode(String target, VerifyCodeType type);
}
