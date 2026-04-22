package com.actionow.ai.plugin.model;

/**
 * AI模型响应模式枚举
 * 定义不同的响应获取方式
 *
 * @author Actionow
 */
public enum ResponseMode {

    /**
     * 阻塞模式
     * 同步等待结果返回，适用于快速响应的模型
     */
    BLOCKING("blocking", "阻塞模式"),

    /**
     * 流式模式
     * 通过SSE实时接收响应流，适用于文本生成等场景
     */
    STREAMING("streaming", "流式模式"),

    /**
     * 回调模式
     * 异步执行，结果通过回调URL通知，适用于长时间任务
     */
    CALLBACK("callback", "回调模式"),

    /**
     * 轮询模式
     * 异步执行，通过轮询查询状态获取结果，适用于无回调支持的场景
     */
    POLLING("polling", "轮询模式");

    private final String code;
    private final String description;

    ResponseMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ResponseMode fromCode(String code) {
        for (ResponseMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown response mode: " + code);
    }
}
