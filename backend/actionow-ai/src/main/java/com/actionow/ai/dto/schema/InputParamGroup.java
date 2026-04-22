package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 输入参数分组
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputParamGroup {

    /**
     * 分组名称（唯一标识）
     */
    private String name;

    /**
     * 显示标签
     */
    private String label;

    /**
     * 英文标签
     */
    private String labelEn;

    /**
     * 分组描述
     */
    private String description;

    /**
     * 排序权重（越小越靠前）
     */
    private Integer order;

    /**
     * 是否默认折叠
     */
    private Boolean collapsed;

    /**
     * 分组图标
     */
    private String icon;

    /**
     * 显示条件
     */
    private DependsOnCondition dependsOn;
}
