package com.actionow.agent.constant;

import lombok.Getter;

/**
 * 模型厂商枚举
 *
 * 支持的 LLM 模型提供商列表
 *
 * @author Actionow
 */
@Getter
public enum ModelProvider {

    /**
     * Google - Gemini 系列
     */
    GOOGLE("GOOGLE", "Google", "Gemini"),

    /**
     * OpenAI - GPT 系列
     */
    OPENAI("OPENAI", "OpenAI", "GPT"),

    /**
     * Anthropic - Claude 系列
     */
    ANTHROPIC("ANTHROPIC", "Anthropic", "Claude"),

    /**
     * 火山引擎 - 豆包系列
     */
    VOLCENGINE("VOLCENGINE", "火山引擎", "豆包"),

    /**
     * 智谱 AI - GLM 系列
     */
    ZHIPU("ZHIPU", "智谱AI", "GLM"),

    /**
     * 月之暗面 - Kimi 系列
     */
    MOONSHOT("MOONSHOT", "月之暗面", "Kimi"),

    /**
     * 百度 - 文心一言系列
     */
    BAIDU("BAIDU", "百度", "文心一言"),

    /**
     * 阿里巴巴 - 通义千问系列
     */
    ALIBABA("ALIBABA", "阿里巴巴", "通义千问");

    /**
     * 厂商代码
     */
    private final String code;

    /**
     * 厂商名称
     */
    private final String name;

    /**
     * 主要产品线
     */
    private final String productLine;

    ModelProvider(String code, String name, String productLine) {
        this.code = code;
        this.name = name;
        this.productLine = productLine;
    }

    /**
     * 根据 code 获取枚举
     */
    public static ModelProvider fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ModelProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 判断是否为国内厂商
     */
    public boolean isDomestic() {
        return this == VOLCENGINE || this == ZHIPU || this == MOONSHOT
                || this == BAIDU || this == ALIBABA;
    }

    /**
     * 判断是否为国际厂商
     */
    public boolean isInternational() {
        return this == GOOGLE || this == OPENAI || this == ANTHROPIC;
    }
}
