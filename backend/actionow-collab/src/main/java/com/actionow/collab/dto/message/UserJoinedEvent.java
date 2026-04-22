package com.actionow.collab.dto.message;

import com.actionow.collab.dto.UserLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户加入事件
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserJoinedEvent {

    /**
     * 剧本ID
     */
    private String scriptId;

    /**
     * 加入的用户
     */
    private UserLocation user;
}
