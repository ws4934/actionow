package com.actionow.task.constant.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 批量作业状态枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum BatchStatusEnum {

    CREATED("CREATED"),
    RUNNING("RUNNING"),
    PAUSED("PAUSED"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BatchStatusEnum fromValue(String value) {
        for (BatchStatusEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        throw new IllegalArgumentException("Unknown BatchStatus: " + value);
    }
}
