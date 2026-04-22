package com.actionow.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 计费配置属性
 * 绑定 application.yml 中 actionow.agent.billing 前缀的配置
 *
 * @author Actionow
 */
@Data
@Component
@ConfigurationProperties(prefix = "actionow.agent.billing")
public class AgentBillingProperties {

    /**
     * 空闲超时时间（分钟），超过此时间未活动的会话将被自动结算
     */
    private int idleTimeoutMinutes = 30;

    /**
     * 每次批量处理的数量
     */
    private int batchSize = 50;

    /**
     * 失败重试的最大次数
     */
    private int maxRetryCount = 3;

    /**
     * 空闲会话结算定时任务执行间隔（毫秒），默认 5 分钟
     */
    private long settleIntervalMs = 300000;

    /**
     * 失败会话重试定时任务执行间隔（毫秒），默认 10 分钟
     */
    private long retryIntervalMs = 600000;

    /**
     * 默认预冻结积分数
     */
    private long defaultFreezeAmount = 100;

    /**
     * 追加冻结阈值比例（已消费/已冻结），达到该比例时自动追加冻结
     */
    private double freezeThresholdRatio = 0.8;

    /**
     * 默认输入价格（积分/1K tokens），在无计费规则时使用
     */
    private String defaultInputPrice = "0.5";

    /**
     * 默认输出价格（积分/1K tokens），在无计费规则时使用
     */
    private String defaultOutputPrice = "1.5";
}
