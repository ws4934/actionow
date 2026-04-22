package com.actionow.common.web.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;

/**
 * 基础控制器
 *
 * @author Actionow
 */
public abstract class BaseController {

    /**
     * 获取当前用户ID
     */
    protected String getCurrentUserId() {
        return UserContextHolder.getUserId();
    }

    /**
     * 获取当前用户名
     */
    protected String getCurrentUsername() {
        return UserContextHolder.getUsername();
    }

    /**
     * 获取当前工作空间ID
     */
    protected String getCurrentWorkspaceId() {
        return UserContextHolder.getWorkspaceId();
    }

    /**
     * 获取当前会话ID
     */
    protected String getCurrentSessionId() {
        return UserContextHolder.getSessionId();
    }

    /**
     * 获取请求ID
     */
    protected String getRequestId() {
        return UserContextHolder.getRequestId();
    }

    /**
     * 成功响应
     */
    protected <T> Result<T> success() {
        return Result.<T>success().requestId(getRequestId());
    }

    /**
     * 成功响应（带数据）
     */
    protected <T> Result<T> success(T data) {
        return Result.success(data).requestId(getRequestId());
    }

    /**
     * 成功响应（带数据和消息）
     */
    protected <T> Result<T> success(T data, String message) {
        return Result.success(data, message).requestId(getRequestId());
    }

    /**
     * 失败响应
     */
    protected <T> Result<T> fail(String message) {
        return Result.<T>fail(message).requestId(getRequestId());
    }

    /**
     * 失败响应
     */
    protected <T> Result<T> fail(String code, String message) {
        return Result.<T>fail(code, message).requestId(getRequestId());
    }
}
