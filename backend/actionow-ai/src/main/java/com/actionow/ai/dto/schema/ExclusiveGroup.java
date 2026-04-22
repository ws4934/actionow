package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 互斥参数组
 * 用于定义一组互斥的参数选项，用户只能选择其中一个
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExclusiveGroup {

    /**
     * 互斥组名称（唯一标识）
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
     * 描述说明
     */
    private String description;

    /**
     * 是否必选
     */
    private Boolean required;

    /**
     * 默认选中的选项值
     */
    private String defaultOption;

    /**
     * 前端组件名称
     * RadioGroup, SegmentedControl, TabSelect
     */
    private String component;

    /**
     * 所属参数分组
     */
    private String group;

    /**
     * 排序权重
     */
    private Integer order;

    /**
     * 选项列表
     */
    private List<ExclusiveOption> options;

    /**
     * 组件属性
     */
    private Object componentProps;
}
