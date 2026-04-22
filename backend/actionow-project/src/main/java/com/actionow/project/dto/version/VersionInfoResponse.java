package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 版本信息响应 DTO
 * 用于版本列表展示
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionInfoResponse {

    /**
     * 版本记录ID
     */
    private String id;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 实体类型
     */
    private String entityType;

    /**
     * 版本号
     */
    private Integer versionNumber;

    /**
     * 变更摘要
     */
    private String changeSummary;

    /**
     * 创建人ID
     */
    private String createdBy;

    /**
     * 创建人名称
     */
    private String createdByName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 是否为当前版本
     */
    private Boolean isCurrent;
}
