package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 互斥组选项
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExclusiveOption {

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
     * 选项描述
     */
    private String description;

    /**
     * 该选项关联的参数名列表
     * 选中此选项时，这些参数会显示
     */
    private List<String> params;

    /**
     * 是否禁用
     */
    private Boolean disabled;
}
