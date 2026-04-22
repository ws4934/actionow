package com.actionow.system.dto;

import lombok.Data;

import java.util.Map;

/**
 * 字典项响应
 *
 * @author Actionow
 */
@Data
public class DictItemResponse {

    private String id;

    private String typeId;

    private String typeCode;

    private String itemCode;

    private String itemName;

    private String itemValue;

    private Map<String, Object> extra;

    private Boolean isDefault;

    private Boolean enabled;

    private Integer sortOrder;
}
