package com.actionow.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 字典类型响应
 *
 * @author Actionow
 */
@Data
public class DictTypeResponse {

    private String id;

    private String typeCode;

    private String typeName;

    private String description;

    private Boolean isSystem;

    private Boolean enabled;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    /**
     * 字典项列表
     */
    private List<DictItemResponse> items;
}
