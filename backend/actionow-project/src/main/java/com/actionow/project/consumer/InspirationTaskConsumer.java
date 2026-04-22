package com.actionow.project.consumer;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.consumer.ConsumerRetryHelper;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.project.service.InspirationRecordService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 灵感任务完成消费者
 * 监听任务完成事件，更新灵感生成记录状态和资产。
 *
 * <p><b>已 deprecated</b>：被 Asset + EntityRelation 统一流程取代。详见
 * {@link com.actionow.project.controller.InspirationController}。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class InspirationTaskConsumer {

    private static final int MAX_RETRIES = 3;

    private final InspirationRecordService inspirationRecordService;
    private final ConsumerRetryHelper retryHelper;

    /**
     * 处理任务完成事件
     */
    @RabbitListener(queues = MqConstants.Inspiration.QUEUE)
    @SuppressWarnings("unchecked")
    public void handleTaskCompleted(MessageWrapper<Map<String, Object>> message,
                                    Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        Map<String, Object> payload = message.getPayload();
        if (payload == null) {
            acknowledgeMessage(channel, deliveryTag);
            return;
        }

        String taskId = (String) payload.get("taskId");
        if (taskId == null) {
            taskId = (String) payload.get("id");
        }
        String status = (String) payload.get("status");

        log.debug("收到任务事件: taskId={}, status={}, messageType={}",
                taskId, status, message.getMessageType());

        // 仅处理终态
        if (!"COMPLETED".equals(status) && !"FAILED".equals(status)) {
            acknowledgeMessage(channel, deliveryTag);
            return;
        }

        // 设置租户上下文
        setTenantContext(message);

        try {
            Map<String, Object> outputResult = (Map<String, Object>) payload.get("outputResult");
            Number creditCost = (Number) payload.get("creditCost");
            String errorMessage = (String) payload.get("errorMessage");
            Map<String, Object> inputParams = (Map<String, Object>) payload.get("inputParams");

            inspirationRecordService.handleTaskCompleted(taskId, status, outputResult,
                    creditCost, errorMessage, inputParams);

            acknowledgeMessage(channel, deliveryTag);
        } catch (Exception e) {
            log.error("处理灵感任务回调失败: taskId={}, error={}", taskId, e.getMessage(), e);
            rejectMessage(channel, deliveryTag, message);
        } finally {
            UserContextHolder.clear();
        }
    }

    private void setTenantContext(MessageWrapper<?> message) {
        UserContext context = new UserContext();
        context.setTenantSchema(message.getTenantSchema());
        context.setWorkspaceId(message.getWorkspaceId());
        context.setUserId(message.getSenderId());
        UserContextHolder.setContext(context);
    }

    private void acknowledgeMessage(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("确认消息失败: deliveryTag={}", deliveryTag, e);
        }
    }

    private void rejectMessage(Channel channel, long deliveryTag, MessageWrapper<?> message) {
        try {
            retryHelper.retryOrDlq(message, channel, deliveryTag, MAX_RETRIES,
                    MqConstants.EXCHANGE_DIRECT, MqConstants.Task.ROUTING_COMPLETED);
        } catch (IOException e) {
            log.error("拒绝消息失败: deliveryTag={}", deliveryTag, e);
        }
    }
}
