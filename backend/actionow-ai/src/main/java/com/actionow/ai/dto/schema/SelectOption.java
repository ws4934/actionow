package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下拉选择选项
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelectOption {

    /**
     * 选项值
     */
    private String value;

    /**
     * 显示标签
     */
    private String label;

    /**
     * 英文标签
     */
    private String labelEn;

    /**
     * 图标
     */
    private String icon;

    /**
     * 是否禁用
     */
    private Boolean disabled;

    /**
     * 选项描述
     */
    private String description;
}
