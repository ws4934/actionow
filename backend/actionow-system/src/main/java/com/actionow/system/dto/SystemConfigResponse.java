package com.actionow.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统配置响应
 *
 * @author Actionow
 */
@Data
public class SystemConfigResponse {

    private String id;

    private String configKey;

    private String configValue;

    private String configType;

    private String scope;

    private String scopeId;

    private String description;

    private String defaultValue;

    private String valueType;

    private Map<String, Object> validation;

    private Boolean enabled;

    private Boolean sensitive;

    private String module;

    private String groupName;

    private String displayName;

    private Integer sortOrder;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
