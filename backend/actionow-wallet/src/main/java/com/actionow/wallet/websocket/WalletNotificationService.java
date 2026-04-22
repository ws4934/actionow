package com.actionow.wallet.websocket;

import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.WebSocketMessage;
import com.actionow.common.mq.producer.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 钱包余额变动通知服务
 * 通过 MQ 发送余额变动通知，由 collab 服务统一推送给前端
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletNotificationService {

    private final MessageProducer messageProducer;

    /**
     * 通知钱包余额变动
     *
     * @param workspaceId     工作空间ID
     * @param balance         变动后可用余额
     * @param frozen          变动后冻结金额
     * @param delta           本次变动金额（正=增加，负=减少）
     * @param transactionType 交易类型（TOPUP/CONSUME/FREEZE/UNFREEZE）
     * @param transactionId   交易记录ID
     */
    public void notifyBalanceChanged(String workspaceId, Long balance, Long frozen,
                                     Long delta, String transactionType, String transactionId) {
        try {
            WebSocketMessage message = WebSocketMessage.walletBalanceChanged(
                    workspaceId, balance, frozen, delta, transactionType, transactionId);
            messageProducer.send(
                    MqConstants.EXCHANGE_DIRECT,
                    MqConstants.Ws.ROUTING_WALLET_BALANCE,
                    MqConstants.Ws.MSG_WALLET_BALANCE,
                    message
            );
            log.debug("Sent wallet balance change notification: workspace={}, delta={}, type={}",
                    workspaceId, delta, transactionType);
        } catch (Exception e) {
            log.error("发送钱包余额变动通知失败: workspaceId={}, type={}, error={}",
                    workspaceId, transactionType, e.getMessage(), e);
        }
    }
}
