package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 参数依赖条件
 * 用于控制参数的显示/隐藏、启用/禁用等
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependsOnCondition {

    // ========== 单条件模式 ==========

    /**
     * 依赖的字段名
     */
    private String field;

    /**
     * 操作符
     * eq, neq, gt, gte, lt, lte, in, notIn, empty, notEmpty, contains, startsWith
     */
    private String operator;

    /**
     * 目标值
     */
    private Object value;

    // ========== 多条件模式 ==========

    /**
     * 逻辑运算符: AND, OR
     */
    private String logic;

    /**
     * 子条件列表
     */
    private List<DependsOnCondition> conditions;

    // ========== 互斥组模式 ==========

    /**
     * 互斥组名称
     */
    private String exclusiveGroup;

    /**
     * 选中的选项值
     */
    private String selectedOption;

    /**
     * 创建单条件
     */
    public static DependsOnCondition of(String field, String operator, Object value) {
        return DependsOnCondition.builder()
                .field(field)
                .operator(operator)
                .value(value)
                .build();
    }

    /**
     * 创建互斥组条件
     */
    public static DependsOnCondition ofExclusiveGroup(String groupName, String selectedOption) {
        return DependsOnCondition.builder()
                .exclusiveGroup(groupName)
                .selectedOption(selectedOption)
                .build();
    }

    /**
     * 创建 AND 组合条件
     */
    public static DependsOnCondition and(List<DependsOnCondition> conditions) {
        return DependsOnCondition.builder()
                .logic("AND")
                .conditions(conditions)
                .build();
    }

    /**
     * 创建 OR 组合条件
     */
    public static DependsOnCondition or(List<DependsOnCondition> conditions) {
        return DependsOnCondition.builder()
                .logic("OR")
                .conditions(conditions)
                .build();
    }

    /**
     * 获取操作符枚举
     */
    public ConditionOperator getOperatorEnum() {
        return ConditionOperator.fromCode(this.operator);
    }
}
