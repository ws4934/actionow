package com.actionow.common.core.result;

/**
 * 响应码接口
 *
 * @author Actionow
 */
public interface IResultCode {

    /**
     * 获取错误码
     */
    String getCode();

    /**
     * 获取错误消息
     */
    String getMessage();
}
