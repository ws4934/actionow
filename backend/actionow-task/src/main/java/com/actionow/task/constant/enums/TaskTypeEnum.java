package com.actionow.task.constant.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务类型枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum TaskTypeEnum {

    IMAGE_GENERATION("IMAGE_GENERATION"),
    VIDEO_GENERATION("VIDEO_GENERATION"),
    AUDIO_GENERATION("AUDIO_GENERATION"),
    TEXT_GENERATION("TEXT_GENERATION"),
    TTS_GENERATION("TTS_GENERATION"),
    BATCH_EXPORT("BATCH_EXPORT"),
    FILE_PROCESSING("FILE_PROCESSING");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TaskTypeEnum fromValue(String value) {
        for (TaskTypeEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        throw new IllegalArgumentException("Unknown TaskType: " + value);
    }
}
