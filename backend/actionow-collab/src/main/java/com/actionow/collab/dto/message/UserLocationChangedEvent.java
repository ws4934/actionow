package com.actionow.collab.dto.message;

import com.actionow.collab.dto.UserLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户位置变化事件
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocationChangedEvent {

    /**
     * 剧本ID
     */
    private String scriptId;

    /**
     * 用户当前位置
     */
    private UserLocation user;

    /**
     * 之前的Tab
     */
    private String previousTab;

    /**
     * 之前聚焦的实体类型
     */
    private String previousEntityType;

    /**
     * 之前聚焦的实体ID
     */
    private String previousEntityId;
}
