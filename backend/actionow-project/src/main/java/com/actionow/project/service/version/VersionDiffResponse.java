package com.actionow.project.service.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 版本差异响应 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDiffResponse {

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 版本1
     */
    private Integer versionNumber1;

    /**
     * 版本2
     */
    private Integer versionNumber2;

    /**
     * 字段变更列表
     */
    private List<FieldDiff> fieldDiffs;

    /**
     * 字段差异详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDiff {

        /**
         * 字段名
         */
        private String fieldName;

        /**
         * 字段显示名
         */
        private String fieldLabel;

        /**
         * 版本1的值
         */
        private Object oldValue;

        /**
         * 版本2的值
         */
        private Object newValue;

        /**
         * 变更类型: ADDED, REMOVED, MODIFIED, UNCHANGED
         */
        private String changeType;
    }
}
