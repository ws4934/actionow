package com.actionow.agent.config.listener;

import com.actionow.agent.registry.DatabaseSkillRegistry;
import com.actionow.agent.saa.factory.SaaAgentFactory;
import com.actionow.agent.saa.factory.SaaChatModelFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 缓存失效监听器
 * 监听 Redis Pub/Sub 消息，实现多实例间的缓存同步失效
 *
 * ## 使用场景
 * 当一个服务实例修改了 Agent 配置并触发热更新时，
 * 通过 Redis Pub/Sub 通知其他实例也清除缓存，确保所有实例使用最新配置。
 *
 * ## 消息通道
 * - agent:config:reload : Agent 配置热更新通知
 * - llm:cache:evict : LLM 缓存清除通知
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener implements MessageListener {

    private static final String CHANNEL_AGENT_RELOAD = "agent:config:reload";
    private static final String CHANNEL_LLM_EVICT = "llm:cache:evict";
    private static final String CHANNEL_SKILL_RELOAD = "agent:skill:reload";

    private final SaaAgentFactory saaAgentFactory;
    private final SaaChatModelFactory saaChatModelFactory;
    private final DatabaseSkillRegistry skillRegistry;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    @PostConstruct
    public void init() {
        // 订阅 Agent 配置热更新通道
        redisMessageListenerContainer.addMessageListener(
                this,
                new ChannelTopic(CHANNEL_AGENT_RELOAD)
        );

        // 订阅 LLM 缓存清除通道
        redisMessageListenerContainer.addMessageListener(
                this,
                new ChannelTopic(CHANNEL_LLM_EVICT)
        );

        // 订阅 Skill 重载通道
        redisMessageListenerContainer.addMessageListener(
                this,
                new ChannelTopic(CHANNEL_SKILL_RELOAD)
        );

        log.info("缓存失效监听器已启动，订阅通道: {}, {}, {}",
                CHANNEL_AGENT_RELOAD, CHANNEL_LLM_EVICT, CHANNEL_SKILL_RELOAD);
    }

    @PreDestroy
    public void destroy() {
        redisMessageListenerContainer.removeMessageListener(this);
        log.info("缓存失效监听器已停止");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            log.info("收到缓存失效通知: channel={}, body={}", channel, body);

            switch (channel) {
                case CHANNEL_AGENT_RELOAD -> handleAgentReload(body);
                case CHANNEL_LLM_EVICT -> handleLlmEvict(body);
                case CHANNEL_SKILL_RELOAD -> handleSkillReload(body);
                default -> log.warn("未知的缓存失效通道: {}", channel);
            }
        } catch (Exception e) {
            log.error("处理缓存失效消息失败", e);
        }
    }

    /**
     * 处理 Agent 配置热更新
     *
     * @param agentType Agent 类型（可为空，表示所有 Agent）
     */
    private void handleAgentReload(String agentType) {
        log.info("处理 Agent 热更新通知: {}", agentType);

        // 清除 LLM 缓存
        saaChatModelFactory.evictAllCache();

        // 使 Agent 缓存失效
        saaAgentFactory.invalidateCache();

        log.info("Agent 缓存已失效，下次请求将使用最新配置");
    }

    /**
     * 处理 Skill 缓存重载
     */
    private void handleSkillReload(String timestamp) {
        log.info("处理 Skill 缓存重载通知: ts={}", timestamp);
        skillRegistry.reload();
        saaAgentFactory.invalidateCache();
        log.info("Skill 缓存已通过 pub/sub 重载，共 {} 个 Skill", skillRegistry.size());
    }

    /**
     * 处理 LLM 缓存清除
     *
     * @param providerId Provider ID（"all" 表示清除所有）
     */
    private void handleLlmEvict(String providerId) {
        log.info("处理 LLM 缓存清除通知: {}", providerId);

        if ("all".equals(providerId)) {
            saaChatModelFactory.evictAllCache();
        } else {
            saaChatModelFactory.evictCache(providerId);
        }
    }
}
