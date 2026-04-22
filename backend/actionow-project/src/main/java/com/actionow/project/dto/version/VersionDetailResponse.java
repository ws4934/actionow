package com.actionow.project.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 版本详情响应 DTO
 * 包含版本的完整数据快照
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDetailResponse {

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

    /**
     * 版本快照数据 (JSON格式)
     * 包含该版本时的完整实体数据
     */
    private Map<String, Object> snapshotData;
}
