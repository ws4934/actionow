package com.actionow.collab.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 出站消息基类 (Server -> Client)
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundMessage {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 消息数据
     */
    private Object data;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 创建出站消息
     */
    public static OutboundMessage of(String type, Object data) {
        return OutboundMessage.builder()
                .type(type)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
