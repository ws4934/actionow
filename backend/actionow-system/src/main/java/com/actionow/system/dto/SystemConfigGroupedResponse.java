package com.actionow.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 系统配置分组响应（按模块 + 分组聚合）
 *
 * @author Actionow
 */
@Data
public class SystemConfigGroupedResponse {

    /**
     * 模块标识
     */
    private String module;

    /**
     * 模块中文名
     */
    private String moduleDisplayName;

    /**
     * 模块下的配置分组
     */
    private List<GroupEntry> groups;

    @Data
    public static class GroupEntry {

        /**
         * 分组名
         */
        private String groupName;

        /**
         * 该分组下的配置列表
         */
        private List<SystemConfigResponse> configs;
    }
}
