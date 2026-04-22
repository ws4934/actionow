package com.actionow.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 模型测试执行请求
 * 用于管理员在前端测试已配置的模型提供商
 *
 * @author Actionow
 */
@Data
public class TestExecutionRequest {

    /**
     * 测试输入参数
     * 按照 inputSchema 定义传入测试数据
     */
    @NotNull(message = "测试输入不能为空")
    private Map<String, Object> inputs;

    /**
     * 响应模式（可选，默认 BLOCKING）
     * BLOCKING / STREAMING / CALLBACK / POLLING
     */
    private String responseMode;

    /**
     * 超时时间覆盖（毫秒，可选）
     */
    private Integer timeoutOverride;
}
