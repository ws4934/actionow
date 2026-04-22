package com.actionow.wallet.consumer;

import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.consumer.ConsumerRetryHelper;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.wallet.service.QuotaService;
import com.actionow.wallet.service.WalletService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 钱包生命周期消费者
 * 处理 Workspace 发出的钱包关闭、配额计划调整等事件
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLifecycleConsumer {

    private static final int MAX_RETRIES = 3;

    private final WalletService walletService;
    private final QuotaService quotaService;
    private final ConsumerRetryHelper retryHelper;

    /**
     * 处理钱包关闭事件
     * Workspace 解散时发送，解冻所有冻结金额并标记钱包为 CLOSED
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConstants.Wallet.QUEUE + ".close", durable = "true",
                    arguments = {
                            @Argument(name = "x-dead-letter-exchange", value = MqConstants.EXCHANGE_DEAD_LETTER),
                            @Argument(name = "x-dead-letter-routing-key", value = MqConstants.QUEUE_DEAD_LETTER)
                    }),
            exchange = @Exchange(value = MqConstants.EXCHANGE_DIRECT, type = "direct"),
            key = MqConstants.Wallet.ROUTING_CLOSE
    ))
    public void handleWalletClose(
            MessageWrapper<Map<String, String>> message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        Map<String, String> payload = message.getPayload();
        String workspaceId = payload != null ? payload.get("workspaceId") : message.getWorkspaceId();
        String operatorId = payload != null ? payload.get("operatorId") : "system";

        log.info("收到钱包关闭消息: workspaceId={}, messageId={}, retryCount={}",
                workspaceId, message.getMessageId(), message.getRetryCount());

        try {
            walletService.closeWallet(workspaceId, operatorId);
            log.info("钱包关闭成功: workspaceId={}", workspaceId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("钱包关闭失败: workspaceId={}, error={}", workspaceId, e.getMessage());
            retryHelper.retryOrDlq(message, channel, deliveryTag, MAX_RETRIES,
                    MqConstants.EXCHANGE_DIRECT, MqConstants.Wallet.ROUTING_CLOSE);
        }
    }

    /**
     * 处理配额计划调整事件
     * Workspace 计划升级/降级时发送，批量调整所有成员的配额上限
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConstants.Wallet.QUEUE + ".adjust-plan", durable = "true",
                    arguments = {
                            @Argument(name = "x-dead-letter-exchange", value = MqConstants.EXCHANGE_DEAD_LETTER),
                            @Argument(name = "x-dead-letter-routing-key", value = MqConstants.QUEUE_DEAD_LETTER)
                    }),
            exchange = @Exchange(value = MqConstants.EXCHANGE_DIRECT, type = "direct"),
            key = MqConstants.Wallet.ROUTING_ADJUST_PLAN
    ))
    public void handleQuotaAdjustPlan(
            MessageWrapper<Map<String, String>> message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        Map<String, String> payload = message.getPayload();
        String workspaceId = payload != null ? payload.get("workspaceId") : message.getWorkspaceId();
        String planType = payload != null ? payload.get("planType") : null;

        log.info("收到配额计划调整消息: workspaceId={}, planType={}, messageId={}, retryCount={}",
                workspaceId, planType, message.getMessageId(), message.getRetryCount());

        if (planType == null || planType.isBlank()) {
            log.error("配额计划调整缺少 planType，丢弃消息: messageId={}", message.getMessageId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            quotaService.adjustQuotasForPlan(workspaceId, planType);
            log.info("配额计划调整成功: workspaceId={}, planType={}", workspaceId, planType);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("配额计划调整失败: workspaceId={}, planType={}, error={}",
                    workspaceId, planType, e.getMessage());
            retryHelper.retryOrDlq(message, channel, deliveryTag, MAX_RETRIES,
                    MqConstants.EXCHANGE_DIRECT, MqConstants.Wallet.ROUTING_ADJUST_PLAN);
        }
    }
}
