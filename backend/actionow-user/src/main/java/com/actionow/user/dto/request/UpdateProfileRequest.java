package com.actionow.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新用户资料请求
 *
 * @author Actionow
 */
@Data
public class UpdateProfileRequest {

    /**
     * 昵称
     */
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    private String nickname;

    /**
     * 头像URL
     */
    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    private String avatarUrl;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
