package com.actionow.collab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户位置
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocation {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 页面类型: SCRIPT_LIST, SCRIPT_DETAIL
     */
    private String page;

    /**
     * 当前剧本ID (仅 SCRIPT_DETAIL)
     */
    private String scriptId;

    /**
     * 当前Tab: DETAIL, EPISODES, STORYBOARDS, CHARACTERS, SCENES, PROPS
     */
    private String tab;

    /**
     * 聚焦的实体类型
     */
    private String focusedEntityType;

    /**
     * 聚焦的实体ID
     */
    private String focusedEntityId;

    /**
     * 协作状态: VIEWING, EDITING
     */
    private String collabStatus;
}
