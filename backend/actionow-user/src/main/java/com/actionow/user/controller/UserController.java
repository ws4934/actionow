package com.actionow.user.controller;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.web.controller.BaseController;
import com.actionow.user.dto.request.BindTargetRequest;
import com.actionow.user.dto.request.ChangePasswordRequest;
import com.actionow.user.dto.request.ResetPasswordRequest;
import com.actionow.user.dto.request.UpdateProfileRequest;
import com.actionow.user.dto.response.UserResponse;
import com.actionow.user.entity.User;
import com.actionow.user.service.AuthService;
import com.actionow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 *
 * @author Actionow
 */
@Tag(name = "用户管理", description = "用户信息查询和管理")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController extends BaseController {

    private final UserService userService;
    private final AuthService authService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    @RequireLogin
    public Result<UserResponse> getCurrentUser() {
        User user = userService.getById(getCurrentUserId());
        if (user == null) {
            return fail("用户不存在");
        }
        return success(userService.toResponse(user));
    }

    @Operation(summary = "更新用户资料")
    @PatchMapping("/me")
    @RequireLogin
    public Result<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(getCurrentUserId(), request);
        User user = userService.getById(getCurrentUserId());
        return success(userService.toResponse(user), "资料更新成功");
    }

    @Operation(summary = "绑定邮箱/手机")
    @PostMapping("/me/bindTarget")
    @RequireLogin
    public Result<Void> bindTarget(@Valid @RequestBody BindTargetRequest request) {
        userService.bindTarget(getCurrentUserId(), request);
        return success(null, "绑定成功");
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    @RequireLogin
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(getCurrentUserId(), request);
        return success(null, "密码修改成功");
    }

    @Operation(summary = "重置密码")
    @PostMapping("/password/reset")
    @IgnoreAuth
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return success(null, "密码重置成功");
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/{userId}")
    @RequireLogin
    public Result<UserResponse> getUser(@PathVariable String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return fail("用户不存在");
        }
        return success(userService.toResponse(user));
    }

    @Operation(summary = "检查用户名是否可用")
    @GetMapping("/check/username")
    @IgnoreAuth
    public Result<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = userService.existsByUsername(username);
        return success(!exists);
    }

    @Operation(summary = "检查邮箱是否可用")
    @GetMapping("/check/email")
    @IgnoreAuth
    public Result<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = userService.existsByEmail(email);
        return success(!exists);
    }

    @Operation(summary = "检查手机号是否可用")
    @GetMapping("/check/phone")
    @IgnoreAuth
    public Result<Boolean> checkPhone(@RequestParam String phone) {
        boolean exists = userService.existsByPhone(phone);
        return success(!exists);
    }
}
