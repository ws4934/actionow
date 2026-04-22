package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参数校验规则
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputParamValidation {

    // ========== 字符串校验 ==========

    /**
     * 最小长度
     */
    private Integer minLength;

    /**
     * 最大长度
     */
    private Integer maxLength;

    /**
     * 正则表达式
     */
    private String pattern;

    /**
     * 正则校验失败提示
     */
    private String patternMessage;

    // ========== 数字校验 ==========

    /**
     * 最小值
     */
    private Number min;

    /**
     * 最大值
     */
    private Number max;

    /**
     * 步进值
     */
    private Number step;

    /**
     * 小数位数
     */
    private Integer precision;

    // ========== 列表校验 ==========

    /**
     * 最少项数
     */
    private Integer minItems;

    /**
     * 最多项数
     */
    private Integer maxItems;

    /**
     * 是否允许重复
     */
    private Boolean uniqueItems;

    // ========== 通用校验 ==========

    /**
     * 自定义校验消息
     */
    private String message;
}
