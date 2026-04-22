package com.actionow.ai.dto.schema;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 条件操作符枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum ConditionOperator {

    EQ("eq", "等于"),
    NEQ("neq", "不等于"),
    GT("gt", "大于"),
    GTE("gte", "大于等于"),
    LT("lt", "小于"),
    LTE("lte", "小于等于"),
    IN("in", "在列表中"),
    NOT_IN("notIn", "不在列表中"),
    EMPTY("empty", "为空"),
    NOT_EMPTY("notEmpty", "不为空"),
    CONTAINS("contains", "包含"),
    STARTS_WITH("startsWith", "以...开头"),
    ENDS_WITH("endsWith", "以...结尾");

    private final String code;
    private final String description;

    public static ConditionOperator fromCode(String code) {
        for (ConditionOperator op : values()) {
            if (op.code.equalsIgnoreCase(code)) {
                return op;
            }
        }
        return EQ;
    }
}
