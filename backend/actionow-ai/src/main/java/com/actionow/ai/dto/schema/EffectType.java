package com.actionow.ai.dto.schema;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 条件作用类型枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum EffectType {

    /**
     * 条件满足时显示，不满足时隐藏
     */
    VISIBILITY("visibility", "显示/隐藏"),

    /**
     * 条件满足时隐藏（与 visibility 相反）
     */
    HIDDEN("hidden", "隐藏"),

    /**
     * 条件满足时启用，不满足时禁用
     */
    DISABLED("disabled", "启用/禁用"),

    /**
     * 条件满足时必填，不满足时选填
     */
    REQUIRED("required", "必填/选填");

    private final String code;
    private final String description;

    public static EffectType fromCode(String code) {
        if (code == null) {
            return VISIBILITY;
        }
        for (EffectType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return VISIBILITY;
    }
}
