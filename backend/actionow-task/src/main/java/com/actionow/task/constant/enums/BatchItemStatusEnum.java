package com.actionow.task.constant.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 批量作业子项状态枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum BatchItemStatusEnum {

    PENDING("PENDING"),
    SUBMITTED("SUBMITTED"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    SKIPPED("SKIPPED"),
    CANCELLED("CANCELLED");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BatchItemStatusEnum fromValue(String value) {
        for (BatchItemStatusEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        throw new IllegalArgumentException("Unknown BatchItemStatus: " + value);
    }
}
