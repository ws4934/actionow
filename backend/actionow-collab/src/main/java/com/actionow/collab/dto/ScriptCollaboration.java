package com.actionow.collab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 剧本协作状态汇总
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptCollaboration {

    /**
     * 剧本ID
     */
    private String scriptId;

    /**
     * 总用户数
     */
    private int totalUsers;

    /**
     * 用户列表
     */
    private List<UserLocation> users;

    /**
     * 每个Tab的用户数
     */
    private Map<String, Integer> tabUserCounts;
}
