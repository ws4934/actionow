package com.actionow.user.controller;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.jwt.JwtClaims;
import com.actionow.common.security.jwt.JwtUtils;
import com.actionow.user.dto.request.TokenValidateRequest;
import com.actionow.user.dto.response.TokenValidateResponse;
import com.actionow.user.dto.response.UserBasicInfo;
import com.actionow.user.entity.User;
import com.actionow.user.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部用户接口（供其他微服务调用）
 *
 * @author Actionow
 */
@Slf4j
@Hidden
@IgnoreAuth
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    /**
     * 根据用户ID获取基本信息
     */
    @GetMapping("/{userId}/basic")
    public Result<UserBasicInfo> getUserBasicInfo(@PathVariable String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.success(null);
        }
        return Result.success(UserBasicInfo.fromUser(user));
    }

    /**
     * 批量获取用户基本信息
     */
    @PostMapping("/batch/basic")
    public Result<Map<String, UserBasicInfo>> batchGetUserBasicInfo(@RequestBody List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Result.success(Map.of());
        }

        List<User> users = userService.listByIds(userIds);
        Map<String, UserBasicInfo> result = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        UserBasicInfo::fromUser
                ));

        return Result.success(result);
    }

    /**
     * 验证Token并返回用户信息
     * 供其他微服务WebSocket认证等场景使用
     */
    @PostMapping("/token/validate")
    public Result<TokenValidateResponse> validateToken(@RequestBody TokenValidateRequest request) {
        String token = request != null ? request.getToken() : null;

        if (token == null || token.isBlank()) {
            return Result.success(TokenValidateResponse.invalid("Token不能为空"));
        }

        try {
            // 验证 Token
            if (!jwtUtils.validateToken(token)) {
                log.debug("Token验证失败: 无效签名或已过期");
                return Result.success(TokenValidateResponse.invalid("Token无效或已过期"));
            }

            // 解析 Token 获取用户信息
            JwtClaims claims = jwtUtils.parseToken(token);

            // 查询用户获取头像（头像可能变化，不存JWT中）
            String avatar = null;
            User user = userService.getById(claims.getUserId());
            if (user != null) {
                avatar = user.getAvatar();
            }

            TokenValidateResponse response = TokenValidateResponse.builder()
                    .valid(true)
                    .userId(claims.getUserId())
                    .username(claims.getUsername())
                    .nickname(claims.getNickname())
                    .email(claims.getEmail())
                    .avatar(avatar)
                    .build();

            log.debug("Token验证成功: userId={}", claims.getUserId());
            return Result.success(response);

        } catch (Exception e) {
            log.warn("Token验证异常: {}", e.getMessage());
            return Result.success(TokenValidateResponse.invalid("Token解析失败: " + e.getMessage()));
        }
    }
}
