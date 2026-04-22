package com.actionow.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SAA (Spring AI Alibaba) Agent 配置属性
 * 替代 v1 的 AdkConfig
 *
 * @author Actionow
 */
@Getter
@Setter
@Component
@ConfigurationProperties("actionow.saa")
public class SaaAgentProperties {

    /**
     * 默认模型 ID（来自 t_llm_provider 表的 provider ID）
     */
    private String defaultModel = "qwen-max";

    /**
     * ReAct 最大迭代次数
     */
    private int maxIterations = 20;

    /**
     * RAG 功能开关
     */
    private boolean ragEnabled = false;
}
