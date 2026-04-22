package com.actionow.wallet.consumer;

import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.consumer.ConsumerRetryHelper;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.wallet.service.WalletService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 钱包创建补偿消费者
 * 当 Workspace 创建后 Wallet 创建失败时，消费补偿消息并重新创建钱包
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletCompensationConsumer {

    private static final int MAX_RETRIES = 3;

    private final WalletService walletService;
    private final ConsumerRetryHelper retryHelper;

    @RabbitListener(queues = MqConstants.Wallet.QUEUE)
    public void handleWalletCreateCompensation(
            MessageWrapper<String> message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        String workspaceId = message.getPayload();
        log.info("收到钱包创建补偿消息: workspaceId={}, messageId={}, retryCount={}",
                workspaceId, message.getMessageId(), message.getRetryCount());

        try {
            walletService.getOrCreateWallet(workspaceId);
            log.info("钱包补偿创建成功: workspaceId={}", workspaceId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("钱包补偿创建失败: workspaceId={}, error={}", workspaceId, e.getMessage());
            retryHelper.retryOrDlq(message, channel, deliveryTag, MAX_RETRIES,
                    MqConstants.EXCHANGE_DIRECT, MqConstants.Wallet.ROUTING_CREATE_COMPENSATION);
        }
    }
}
